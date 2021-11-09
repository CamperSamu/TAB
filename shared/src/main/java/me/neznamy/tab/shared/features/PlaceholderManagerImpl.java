package me.neznamy.tab.shared.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;

import me.neznamy.tab.api.PlaceholderManager;
import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.task.RepeatingTask;
import me.neznamy.tab.shared.CpuConstants;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.placeholders.Placeholder;
import me.neznamy.tab.shared.placeholders.PlayerPlaceholder;
import me.neznamy.tab.shared.placeholders.RelationalPlaceholder;
import me.neznamy.tab.shared.placeholders.ServerPlaceholder;

/**
 * Messy class for placeholder management
 */
public class PlaceholderManagerImpl extends TabFeature implements PlaceholderManager {

	private final Pattern placeholderPattern = Pattern.compile("%([^%]*)%");

	private int defaultRefresh;
	private Map<String, Integer> serverPlaceholderRefreshIntervals = new HashMap<>();
	private Map<String, Integer> playerPlaceholderRefreshIntervals = new HashMap<>();
	private Map<String, Integer> relationalPlaceholderRefreshIntervals = new HashMap<>();

	//plugin internals + PAPI + API
	private Map<String, Placeholder> registeredPlaceholders = new HashMap<>();

	//map of String-Set of features using placeholder
	private Map<String, Set<TabFeature>> placeholderUsage = new ConcurrentHashMap<>();
	private Placeholder[] usedPlaceholders = new Placeholder[0];
	
	private AtomicInteger atomic = new AtomicInteger();
	private RepeatingTask refreshTask;

	public PlaceholderManagerImpl(){
		super("Refreshing placeholders");
		loadRefreshIntervals();
		refreshTask = TAB.getInstance().getCPUManager().startRepeatingMeasuredTask(100000, "refreshing placeholders", this, "Refreshing placeholders", this::refresh);
	}
	
	private void refresh() {
		int loopTime = atomic.addAndGet(refreshTask.getInterval());
		int size = TAB.getInstance().getOnlinePlayers().length;
		Map<TabPlayer, Set<TabFeature>> update = new HashMap<>(size);
		Map<TabPlayer, Set<TabFeature>> forceUpdate = new HashMap<>(size);
		boolean somethingChanged = false;
		for (Placeholder placeholder : usedPlaceholders) {
			if (loopTime % placeholder.getRefresh() != 0) continue;
			if (placeholder instanceof RelationalPlaceholder && updateRelationalPlaceholder((RelationalPlaceholder) placeholder, forceUpdate)) somethingChanged = true;
			if (placeholder instanceof PlayerPlaceholder && updatePlayerPlaceholder((PlayerPlaceholder) placeholder, update)) somethingChanged = true;
			if (placeholder instanceof ServerPlaceholder && updateServerPlaceholder((ServerPlaceholder) placeholder, update)) somethingChanged = true;
		}
		if (somethingChanged) refresh(forceUpdate, update);
	}
	
	private void refresh(Map<TabPlayer, Set<TabFeature>> forceUpdate, Map<TabPlayer, Set<TabFeature>> update) {
		for (Entry<TabPlayer, Set<TabFeature>> entry : update.entrySet()) {
			if (forceUpdate.containsKey(entry.getKey())) {
				entry.getValue().removeAll(forceUpdate.get(entry.getKey()));
			}
		}
		long startRefreshTime = System.nanoTime();
		for (Entry<TabPlayer, Set<TabFeature>> entry : forceUpdate.entrySet()) {
			for (TabFeature r : entry.getValue()) {
				long startTime = System.nanoTime();
				r.refresh(entry.getKey(), true);
				TAB.getInstance().getCPUManager().addTime(r.getFeatureName(), CpuConstants.UsageCategory.UPDATING, System.nanoTime()-startTime);
			}
		}
		for (Entry<TabPlayer, Set<TabFeature>> entry : update.entrySet()) {
			for (TabFeature r : entry.getValue()) {
				long startTime = System.nanoTime();
				r.refresh(entry.getKey(), false);
				TAB.getInstance().getCPUManager().addTime(r.getFeatureName(), CpuConstants.UsageCategory.UPDATING, System.nanoTime()-startTime);
			}
		}
		//subtracting back usage by this method from placeholder refreshing usage, since it is already counted under different name in this method
		TAB.getInstance().getCPUManager().addTime(getFeatureName(), CpuConstants.UsageCategory.PLACEHOLDER_REFRESHING, startRefreshTime-System.nanoTime());
	}

	private boolean updateRelationalPlaceholder(RelationalPlaceholder placeholder, Map<TabPlayer, Set<TabFeature>> forceUpdate) {
		boolean somethingChanged = false;
		long startTime = System.nanoTime();
		for (TabPlayer p1 : TAB.getInstance().getOnlinePlayers()) {
			if (!p1.isLoaded()) continue;
			for (TabPlayer p2 : TAB.getInstance().getOnlinePlayers()) {
				if (!p2.isLoaded()) continue;
				if (placeholder.update(p1, p2)) {
					forceUpdate.computeIfAbsent(p2, x -> new HashSet<>()).addAll(placeholderUsage.get(placeholder.getIdentifier()));
					somethingChanged = true;
				}
				if (placeholder.update(p2, p1)) {
					forceUpdate.computeIfAbsent(p1, x -> new HashSet<>()).addAll(placeholderUsage.get(placeholder.getIdentifier()));
					somethingChanged = true;
				}
			}
		}
		TAB.getInstance().getCPUManager().addPlaceholderTime(placeholder.getIdentifier(), System.nanoTime()-startTime);
		return somethingChanged;
	}

	private boolean updatePlayerPlaceholder(PlayerPlaceholder placeholder, Map<TabPlayer, Set<TabFeature>> update) {
		boolean somethingChanged = false;
		long startTime = System.nanoTime();
		for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
			if (all.isLoaded() && placeholder.update(all)) {
				update.computeIfAbsent(all, k -> new HashSet<>()).addAll(placeholderUsage.get(placeholder.getIdentifier()));
				somethingChanged = true;
			}
		}
		TAB.getInstance().getCPUManager().addPlaceholderTime(placeholder.getIdentifier(), System.nanoTime()-startTime);
		return somethingChanged;
	}

	private boolean updateServerPlaceholder(ServerPlaceholder placeholder, Map<TabPlayer, Set<TabFeature>> update) {
		boolean somethingChanged = false;
		long startTime = System.nanoTime();
		if (placeholder.update()) {
			somethingChanged = true;
			for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
				if (!all.isLoaded()) continue;
				update.computeIfAbsent(all, k -> new HashSet<>()).addAll(placeholderUsage.get(placeholder.getIdentifier()));
			}
		}
		TAB.getInstance().getCPUManager().addPlaceholderTime(placeholder.getIdentifier(), System.nanoTime()-startTime);
		return somethingChanged;
	}

	private void loadRefreshIntervals() {
		for (Object category : TAB.getInstance().getConfiguration().getConfig().getConfigurationSection("placeholderapi-refresh-intervals").keySet()) {
			if (!Arrays.asList("default-refresh-interval", "server", "player", "relational").contains(category.toString())) {
				TAB.getInstance().getErrorManager().startupWarn("Unknown placeholder category \"" + category + "\". Valid categories are \"server\", \"player\" and \"relational\"");
			}
		}
		defaultRefresh = TAB.getInstance().getConfiguration().getConfig().getInt("placeholderapi-refresh-intervals.default-refresh-interval", 100);
		for (Entry<Object, Object> placeholder : TAB.getInstance().getConfiguration().getConfig().getConfigurationSection("placeholderapi-refresh-intervals.server").entrySet()) {
			serverPlaceholderRefreshIntervals.put(placeholder.getKey().toString(), TAB.getInstance().getErrorManager().parseInteger(placeholder.getValue().toString(), getDefaultRefresh(), "refresh interval of server placeholder"));
		}
		for (Entry<Object, Object> placeholder : TAB.getInstance().getConfiguration().getConfig().getConfigurationSection("placeholderapi-refresh-intervals.player").entrySet()) {
			playerPlaceholderRefreshIntervals.put(placeholder.getKey().toString(), TAB.getInstance().getErrorManager().parseInteger(placeholder.getValue().toString(), getDefaultRefresh(), "refresh interval of player placeholder"));
		}
		for (Entry<Object, Object> placeholder : TAB.getInstance().getConfiguration().getConfig().getConfigurationSection("placeholderapi-refresh-intervals.relational").entrySet()) {
			relationalPlaceholderRefreshIntervals.put(placeholder.getKey().toString(), TAB.getInstance().getErrorManager().parseInteger(placeholder.getValue().toString(), getDefaultRefresh(), "refresh interval of relational placeholder"));
		}
	}

	public int getRelationalRefresh(String identifier) {
		if (relationalPlaceholderRefreshIntervals.containsKey(identifier)) {
			return relationalPlaceholderRefreshIntervals.get(identifier);
		} else {
			return defaultRefresh;
		}
	}

	public Collection<Placeholder> getAllPlaceholders(){
		return new ArrayList<>(registeredPlaceholders.values());
	}

	public void registerPlaceholder(Placeholder placeholder) {
		Preconditions.checkNotNull(placeholder, "placeholder");
		registeredPlaceholders.put(placeholder.getIdentifier(), placeholder);
		usedPlaceholders = placeholderUsage.keySet().stream().map(this::getPlaceholder).collect(Collectors.toSet()).toArray(new Placeholder[0]);
	}
	
	public Placeholder[] getUsedPlaceholders() {
		return usedPlaceholders;
	}
	
	public Map<String, Integer> getServerPlaceholderRefreshIntervals() {
		return serverPlaceholderRefreshIntervals;
	}

	public Map<String, Integer> getPlayerPlaceholderRefreshIntervals() {
		return playerPlaceholderRefreshIntervals;
	}

	public int getDefaultRefresh() {
		return defaultRefresh;
	}
	
	@Override
	public void load() {
		for (Placeholder pl : usedPlaceholders) {
			if (pl instanceof ServerPlaceholder) {
				((ServerPlaceholder)pl).update();
			}
		}
		for (TabPlayer p : TAB.getInstance().getOnlinePlayers()) {
			onJoin(p);
		}
	}

	@Override
	public void onJoin(TabPlayer connectedPlayer) {
		for (Placeholder pl : usedPlaceholders) {
			if (pl instanceof RelationalPlaceholder) {
				for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
					((RelationalPlaceholder)pl).update(connectedPlayer, all);
					((RelationalPlaceholder)pl).update(all, connectedPlayer);
				}
			}
			if (pl instanceof PlayerPlaceholder) {
				((PlayerPlaceholder)pl).update(connectedPlayer);
			}
		}
	}

	@Override
	public void onQuit(TabPlayer disconnectedPlayer) {
		for (Placeholder pl : usedPlaceholders) {
			if (pl instanceof RelationalPlaceholder) {
				for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
					((RelationalPlaceholder)pl).getLastValues().remove(all.getName() + "-" + disconnectedPlayer.getName());
					((RelationalPlaceholder)pl).getLastValues().remove(disconnectedPlayer.getName() + "-" + all.getName());
				}
			}
			if (pl instanceof PlayerPlaceholder) {
				((PlayerPlaceholder)pl).getLastValues().remove(disconnectedPlayer.getName());
				((PlayerPlaceholder)pl).getForceUpdate().remove(disconnectedPlayer.getName());
			}
		}
	}

	@Override
	public void registerServerPlaceholder(String identifier, int refresh, Supplier<Object> supplier) {
		registerPlaceholder(new ServerPlaceholder(identifier, refresh, supplier));
	}
	
	@Override
	public void registerPlayerPlaceholder(String identifier, int refresh, Function<TabPlayer, Object> function) {
		registerPlaceholder(new PlayerPlaceholder(identifier, refresh, function));
	}

	@Override
	public void registerRelationalPlaceholder(String identifier, int refresh, BiFunction<TabPlayer, TabPlayer, Object> function) {
		registerPlaceholder(new RelationalPlaceholder(identifier, refresh, function));
	}

	@Override
	public List<String> detectPlaceholders(String text){
		List<String> placeholders = new ArrayList<>();
		if (text == null || !text.contains("%")) return placeholders;
		Matcher m = placeholderPattern.matcher(text);
		while (m.find()) {
			placeholders.add(m.group());
		}
		return placeholders;
	}


	public Placeholder getPlaceholder(String identifier) {
		Placeholder p = registeredPlaceholders.get(identifier);
		if (p == null) {
			TAB.getInstance().getPlatform().registerUnknownPlaceholder(identifier);
			addUsedPlaceholder(identifier, this); //likely used via tab expansion
			return getPlaceholder(identifier);
		}
		if (!placeholderUsage.containsKey(identifier)) {
			//tab expansion for internal placeholder
			addUsedPlaceholder(identifier, this);
		}
		return p;
	}

	@Override
	public void addUsedPlaceholder(String identifier, TabFeature feature) {
		if (placeholderUsage.computeIfAbsent(identifier, x -> new HashSet<>()).add(feature)) {
			usedPlaceholders = placeholderUsage.keySet().stream().map(this::getPlaceholder).collect(Collectors.toSet()).toArray(new Placeholder[0]);
			Placeholder p = getPlaceholder(identifier);
			if (p.getRefresh() % 50 == 0 && p.getRefresh() > 0 && refreshTask.getInterval() > p.getRefresh()) {
				TAB.getInstance().debug("Decreasing refresh interval of placeholder refreshing task to " + p.getRefresh() + "ms due to placeholder " + identifier);
				refreshTask.setInterval(p.getRefresh());
				atomic.set(0);
			} 
		}
	}

	@Override
	public String findReplacement(String placeholder, String output) {
		return getPlaceholder(placeholder).getReplacements().findReplacement(output);
	}
}
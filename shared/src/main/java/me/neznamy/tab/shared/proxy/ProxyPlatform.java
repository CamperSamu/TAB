package me.neznamy.tab.shared.proxy;

import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.Placeholder;
import me.neznamy.tab.shared.Platform;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.features.PlaceholderManagerImpl;
import me.neznamy.tab.shared.features.PluginMessageHandler;
import me.neznamy.tab.shared.features.bossbar.BossBarManagerImpl;
import me.neznamy.tab.shared.features.globalplayerlist.GlobalPlayerList;
import me.neznamy.tab.shared.features.nametags.NameTag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract class containing common variables and methods
 * shared between proxies.
 */
public abstract class ProxyPlatform implements Platform {

	/** Plugin message handler for sending and receiving plugin messages */
	protected final PluginMessageHandler plm;

	/** Placeholders which are refreshed on backend server */
	private final Map<String, Integer> bridgePlaceholders = new ConcurrentHashMap<>();

	/**
	 * Constructs new instance with given parameter
	 *
	 * @param	plm
	 * 			Plugin message handler
	 */
	protected ProxyPlatform(PluginMessageHandler plm) {
		this.plm = plm;
	}

	/**
	 * Returns plugin message handler
	 *
	 * @return	plugin message handler
	 */
	public PluginMessageHandler getPluginMessageHandler() {
		return plm;
	}

	/**
	 * Returns bridge placeholders, which are refreshed on backend server
	 *
	 * @return	bridge placeholders, which are refreshed on backend server
	 */
	public Map<String, Integer> getBridgePlaceholders() {
		return bridgePlaceholders;
	}

	@Override
	public void registerUnknownPlaceholder(String identifier) {
		PlaceholderManagerImpl pl = TAB.getInstance().getPlaceholderManager();
		Placeholder placeholder;
		if (identifier.startsWith("%rel_")) {
			placeholder = pl.registerRelationalPlaceholder(identifier, pl.getRelationalRefresh(identifier), (viewer, target) -> null);
		} else {
			int refresh = pl.getPlayerPlaceholderRefreshIntervals().getOrDefault(identifier, pl.getServerPlaceholderRefreshIntervals().getOrDefault(identifier, pl.getDefaultRefresh()));
			placeholder = pl.registerPlayerPlaceholder(identifier, refresh, player -> null);
		}
		placeholder.enableTriggerMode();
		bridgePlaceholders.put(placeholder.getIdentifier(), placeholder.getRefresh());
		for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
			plm.sendMessage(all, "Placeholder", placeholder.getIdentifier(), placeholder.getRefresh());
		}
	}
	
	@Override
	public void loadFeatures() {
		TAB tab = TAB.getInstance();
		if (tab.getConfiguration().getConfig().getBoolean("scoreboard-teams.enabled", true))
			tab.getFeatureManager().registerFeature(TabConstants.Feature.NAME_TAGS, new NameTag());
		tab.loadUniversalFeatures();
		if (tab.getConfiguration().getConfig().getBoolean("bossbar.enabled", false))
			tab.getFeatureManager().registerFeature(TabConstants.Feature.BOSS_BAR, new BossBarManagerImpl());
		if (tab.getConfiguration().getConfig().getBoolean("global-playerlist.enabled", false))
			tab.getFeatureManager().registerFeature(TabConstants.Feature.GLOBAL_PLAYER_LIST, new GlobalPlayerList());
		if (tab.getConfiguration().getConfig().getBoolean("fix-pet-names.enabled", false))
			tab.getFeatureManager().registerFeature(TabConstants.Feature.PET_FIX, new TabFeature("", "") {});
	}

	@Override
	public String getConfigName() {
		return "proxyconfig.yml";
	}
}

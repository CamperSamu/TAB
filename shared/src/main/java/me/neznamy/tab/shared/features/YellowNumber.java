package me.neznamy.tab.shared.features;

import java.util.Arrays;

import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.protocol.PacketPlayOutScoreboardObjective;
import me.neznamy.tab.api.protocol.PacketPlayOutScoreboardObjective.EnumScoreboardHealthDisplay;
import me.neznamy.tab.api.protocol.PacketPlayOutScoreboardScore;
import me.neznamy.tab.api.protocol.PacketPlayOutScoreboardScore.Action;
import me.neznamy.tab.shared.PacketAPI;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;

/**
 * Feature handler for tablist objective feature
 */
public class YellowNumber extends TabFeature {

	public static final String OBJECTIVE_NAME = "TAB-YellowNumber";
	public static final int DISPLAY_SLOT = 0;
	private static final String TITLE = "PlayerlistObjectiveTitle";

	private final String rawValue = TAB.getInstance().getConfiguration().getConfig().getString("yellow-number-in-tablist.value", "%ping%");
	private final EnumScoreboardHealthDisplay displayType = "%health%".equals(rawValue) || "%player_health%".equals(rawValue) || 
			"%player_health_rounded%".equals(rawValue) ? EnumScoreboardHealthDisplay.HEARTS : EnumScoreboardHealthDisplay.INTEGER;

	public YellowNumber() {
		super("Yellow number", "Updating value", TAB.getInstance().getConfiguration().getConfig().getStringList("yellow-number-in-tablist.disable-in-servers"),
				TAB.getInstance().getConfiguration().getConfig().getStringList("yellow-number-in-tablist.disable-in-worlds"));
		TAB.getInstance().debug(String.format("Loaded YellowNumber feature with parameters value=%s, disabledWorlds=%s, disabledServers=%s, displayType=%s", rawValue, Arrays.toString(disabledWorlds), Arrays.toString(disabledServers), displayType));
	}

	@Override
	public void load() {
		for (TabPlayer loaded : TAB.getInstance().getOnlinePlayers()){
			loaded.setProperty(this, TabConstants.Property.YELLOW_NUMBER, rawValue);
			if (isDisabled(loaded.getServer(), loaded.getWorld())) {
				addDisabledPlayer(loaded);
				continue;
			}
			PacketAPI.registerScoreboardObjective(loaded, OBJECTIVE_NAME, TITLE, DISPLAY_SLOT, displayType, this);
		}
		for (TabPlayer viewer : TAB.getInstance().getOnlinePlayers()){
			for (TabPlayer target : TAB.getInstance().getOnlinePlayers()){
				viewer.sendCustomPacket(new PacketPlayOutScoreboardScore(Action.CHANGE, OBJECTIVE_NAME, getName(target), getValue(target)), this);
			}
		}
	}

	@Override
	public void unload() {
		for (TabPlayer p : TAB.getInstance().getOnlinePlayers()){
			if (isDisabledPlayer(p)) continue;
			p.sendCustomPacket(new PacketPlayOutScoreboardObjective(OBJECTIVE_NAME), this);
		}
	}

	@Override
	public void onJoin(TabPlayer connectedPlayer) {
		connectedPlayer.setProperty(this, TabConstants.Property.YELLOW_NUMBER, rawValue);
		if (isDisabled(connectedPlayer.getServer(), connectedPlayer.getWorld())) {
			addDisabledPlayer(connectedPlayer);
			return;
		}
		PacketAPI.registerScoreboardObjective(connectedPlayer, OBJECTIVE_NAME, TITLE, DISPLAY_SLOT, displayType, this);
		int value = getValue(connectedPlayer);
		for (TabPlayer all : TAB.getInstance().getOnlinePlayers()){
			all.sendCustomPacket(new PacketPlayOutScoreboardScore(Action.CHANGE, OBJECTIVE_NAME, getName(connectedPlayer), value), this);
			if (all.isLoaded()) connectedPlayer.sendCustomPacket(new PacketPlayOutScoreboardScore(Action.CHANGE, OBJECTIVE_NAME, getName(all), getValue(all)), this);
		}
	}

	@Override
	public void onWorldChange(TabPlayer p, String from, String to) {
		boolean disabledBefore = isDisabledPlayer(p);
		boolean disabledNow = false;
		if (isDisabled(p.getServer(), p.getWorld())) {
			disabledNow = true;
			addDisabledPlayer(p);
		} else {
			removeDisabledPlayer(p);
		}
		if (disabledNow && !disabledBefore) {
			p.sendCustomPacket(new PacketPlayOutScoreboardObjective(OBJECTIVE_NAME), this);
		}
		if (!disabledNow && disabledBefore) {
			onJoin(p);
			RedisSupport redis = (RedisSupport) TAB.getInstance().getFeatureManager().getFeature("redisbungee");
			if (redis != null) redis.updateYellowNumber(p, p.getProperty(TabConstants.Property.YELLOW_NUMBER).get());
		}
	}

	public int getValue(TabPlayer p) {
		return TAB.getInstance().getErrorManager().parseInteger(p.getProperty(TabConstants.Property.YELLOW_NUMBER).updateAndGet(), 0, "yellow number");
	}

	@Override
	public void refresh(TabPlayer refreshed, boolean force) {
		if (isDisabledPlayer(refreshed)) return;
		int value = getValue(refreshed);
		for (TabPlayer all : TAB.getInstance().getOnlinePlayers()) {
			all.sendCustomPacket(new PacketPlayOutScoreboardScore(Action.CHANGE, OBJECTIVE_NAME, getName(refreshed), value), this);
		}
		RedisSupport redis = (RedisSupport) TAB.getInstance().getFeatureManager().getFeature("redisbungee");
		if (redis != null) redis.updateYellowNumber(refreshed, refreshed.getProperty(TabConstants.Property.YELLOW_NUMBER).get());
	}

	private String getName(TabPlayer p) {
		return ((NickCompatibility) TAB.getInstance().getFeatureManager().getFeature("nick")).getNickname(p);
	}
}
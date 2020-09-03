package me.neznamy.tab.shared;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.netty.channel.Channel;
import me.neznamy.tab.api.EnumProperty;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.platforms.bukkit.features.unlimitedtags.ArmorStandManager;
import me.neznamy.tab.platforms.bukkit.nms.PacketPlayOut;
import me.neznamy.tab.premium.SortingType;
import me.neznamy.tab.premium.scoreboard.Scoreboard;
import me.neznamy.tab.premium.scoreboard.ScoreboardManager;
import me.neznamy.tab.shared.command.level1.PlayerCommand;
import me.neznamy.tab.shared.config.Configs;
import me.neznamy.tab.shared.features.bossbar.BossBarLine;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.EnumGamemode;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.PlayerInfoData;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerListHeaderFooter;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardTeam;
import me.neznamy.tab.shared.packets.UniversalPacketPlayOut;
import me.neznamy.tab.shared.placeholders.Placeholders;
import net.md_5.bungee.api.connection.ProxiedPlayer;

/**
 * The core class for player
 */
public abstract class ITabPlayer implements TabPlayer {

	public String name;
	public UUID uniqueId;
	public UUID offlineId;
	public UUID correctId; //for velocity, the correct UUID to use in tablist
	public String world;
	private String permissionGroup = "< Not Initialized Yet >";
	public String teamName;
	public String teamNameNote;

	public Map<String, Property> properties = new HashMap<String, Property>();
	private ArmorStandManager armorStandManager;
	protected ProtocolVersion version = ProtocolVersion.SERVER_VERSION;
	public Channel channel;
	public boolean nameTagVisible = true;
	public boolean bossbarVisible;

	public boolean disabledHeaderFooter;
	public boolean disabledTablistNames;
	public boolean disabledNametag;
	public boolean disabledTablistObjective;
	public boolean disabledBossbar;
	public boolean disabledBelowname;

	private boolean previewingNametag;
	public List<BossBarLine> activeBossBars = new ArrayList<BossBarLine>();
	public boolean lastCollision;
	public boolean lastVisibility;
	public boolean onJoinFinished;
	public boolean hiddenNametag;
	
	private Scoreboard activeScoreboard;
	public boolean hiddenScoreboard;
	public Scoreboard forcedScoreboard;

	public void init() {
		updateDisabledWorlds(getWorldName());
		updateGroupIfNeeded(false);
		offlineId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
		correctId = uniqueId; //initialization to avoid NPEs
	}

	//bukkit only
	public boolean hasInvisibility() {
		return false;
	}

	//per-type

	public abstract boolean hasPermission(String permission);

	public abstract long getPing();

	public abstract void sendPacket(Object nmsPacket);

	public abstract void sendMessage(String message, boolean translateColors);

	public abstract Object getSkin();
	
	public boolean isVanished() {
		return false;
	}

	public boolean getTeamPush() {
		return Configs.getCollisionRule(world);
	}

	public String getName() {
		return name;
	}

	public UUID getUniqueId() {
		return uniqueId;
	}

	public UUID getOfflineId() {
		return offlineId;
	}

	public boolean isStaff() {
		return hasPermission("tab.staff");
	}

	public void setActiveScoreboard(Scoreboard board) {
		activeScoreboard = board;
	}

	public Scoreboard getActiveScoreboard() {
		return activeScoreboard;
	}

	public PlayerInfoData getInfoData() {
		return new PlayerInfoData(name, correctId, null, 0, EnumGamemode.CREATIVE, null);
	}
	
	public String getGroupFromPermPlugin() {
		try {
			return Shared.permissionPlugin.getPrimaryGroup(this);
		} catch (Throwable e) {
			return Shared.errorManager.printError("null", "Failed to get permission group of " + getName() + " using " + Shared.permissionPlugin.getName(), e);
		}
	}

	public String[] getGroupsFromPermPlugin() {
		try {
			return Shared.permissionPlugin.getAllGroups(this);
		} catch (Throwable e) {
			return Shared.errorManager.printError(new String[] {"null"}, "Failed to get permission groups of " + getName() + " using " + Shared.permissionPlugin.getName(), e);
		}
	}

	public String setProperty(String identifier, String rawValue, String source) {
		Property p = getProperty(identifier);
		if (p == null) {
			properties.put(identifier, new Property(this, rawValue, source));
		} else {
			p.changeRawValue(rawValue);
			p.setSource(source);
		}
		return rawValue;
	}

	public void updateTeam() {
		if (disabledNametag) return;
		if (teamName == null) return; //player not loaded yet
		String newName = SortingType.INSTANCE.getTeamName(this);
		if (teamName.equals(newName)) {
			updateTeamData();
		} else {
			unregisterTeam();
			teamName = newName;
			registerTeam();
		}
	}

	private boolean getTeamVisibility() {
		if (hiddenNametag || Configs.SECRET_invisible_nametags) return false;
		return !Shared.featureManager.isFeatureEnabled("nametagx") && nameTagVisible;
	}

	public void updateGroupIfNeeded(boolean updateDataIfChanged) {
		String newGroup = "null";
		if (Configs.groupsByPermissions) {
			for (Object group : Configs.primaryGroupFindingList) {
				if (hasPermission("tab.group." + group)) {
					newGroup = String.valueOf(group);
					break;
				}
			}
			if (newGroup.equals("null")) {
				Shared.errorManager.oneTimeConsoleError("Player " + name + " does not have any group permission while assign-groups-by-permissions is enabled! Did you forget to add his group to primary-group-finding-list?");
			}
		} else {
			if (Configs.usePrimaryGroup) {
				newGroup = getGroupFromPermPlugin();
			} else {
				String[] playerGroups = getGroupsFromPermPlugin();
				if (playerGroups != null && playerGroups.length > 0) {
					loop:
						for (Object entry : Configs.primaryGroupFindingList) {
							for (String playerGroup : playerGroups) {
								if (playerGroup.equalsIgnoreCase(entry + "")) {
									newGroup = playerGroup;
									break loop;
								}
							}
						}
				if (playerGroups[0] != null && newGroup.equals("null")) newGroup = playerGroups[0];
				}
			}
		}
		if (!permissionGroup.equals(newGroup)) {
			permissionGroup = newGroup;
			if (updateDataIfChanged) {
				forceRefresh();
			}
		}
	}

	public void updateProperty(String property) {
		updateProperty(property, "");
	}
	public void updateProperty(String property, String ifnull) {
		String playerGroupFromConfig = permissionGroup.replace(".", "@#@");
		String worldGroup = getWorldGroupOf(getWorldName());
		String value;
		if ((value = Configs.config.getString("per-" + Shared.platform.getSeparatorType() + "-settings." + worldGroup + ".Users." + getName() + "." + property)) != null) {
			setProperty(property, value, "Player: " + getName() + ", " + Shared.platform.getSeparatorType() + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.platform.getSeparatorType() + "-settings." + worldGroup + ".Users." + getUniqueId().toString() + "." + property)) != null) {
			setProperty(property, value, "PlayerUUID: " + getName() + ", " + Shared.platform.getSeparatorType() + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("Users." + getName() + "." + property)) != null) {
			setProperty(property, value, "Player: " + getName());
			return;
		}
		if ((value = Configs.config.getString("Users." + getUniqueId().toString() + "." + property)) != null) {
			setProperty(property, value, "PlayerUUID: " + getName());
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.platform.getSeparatorType() + "-settings." + worldGroup + ".Groups." + playerGroupFromConfig + "." + property)) != null) {
			setProperty(property, value, "Group: " + permissionGroup + ", " + Shared.platform.getSeparatorType() + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("per-" + Shared.platform.getSeparatorType() + "-settings." + worldGroup + ".Groups._OTHER_." + property)) != null) {
			setProperty(property, value, "Group: _OTHER_," + Shared.platform.getSeparatorType() + ": " + worldGroup);
			return;
		}
		if ((value = Configs.config.getString("Groups." + playerGroupFromConfig + "." + property)) != null) {
			setProperty(property, value, "Group: " + permissionGroup);
			return;
		}
		if ((value = Configs.config.getString("Groups._OTHER_." + property)) != null) {
			setProperty(property, value, "Group: _OTHER_");
			return;
		}
		setProperty(property, ifnull, "None");
	}

	public String getWorldGroupOf(String world) {
		Map<String, Object> worlds = Configs.config.getConfigurationSection("per-" + Shared.platform.getSeparatorType() + "-settings");
		if (worlds.isEmpty()) return world;
		for (String worldGroup : worlds.keySet()) {
			for (String localWorld : worldGroup.split(Configs.SECRET_multiWorldSeparator)) {
				if (localWorld.equalsIgnoreCase(world)) return worldGroup;
			}
		}
		return world;
	}

	public void updateTeamData() {
		if (disabledNametag) return;
		Property tagprefix = getProperty("tagprefix");
		Property tagsuffix = getProperty("tagsuffix");
		boolean collision = lastCollision = getTeamPush();
		boolean visible = getTeamVisibility();
		for (ITabPlayer viewer : Shared.getPlayers()) {
			String currentPrefix = tagprefix.getFormat(viewer);
			String currentSuffix = tagsuffix.getFormat(viewer);
			viewer.sendCustomPacket(PacketPlayOutScoreboardTeam.UPDATE_TEAM_INFO(teamName, currentPrefix, currentSuffix, visible?"always":"never", collision?"always":"never", 69));
		}
	}

	public void registerTeam() {
		Property tagprefix = getProperty("tagprefix");
		Property tagsuffix = getProperty("tagsuffix");
		for (ITabPlayer viewer : Shared.getPlayers()) {
			String currentPrefix = tagprefix.getFormat(viewer);
			String currentSuffix = tagsuffix.getFormat(viewer);
			PacketAPI.registerScoreboardTeam(viewer, teamName, currentPrefix, currentSuffix, lastVisibility = getTeamVisibility(), lastCollision = getTeamPush(), Arrays.asList(getName()), null);
		}
	}

	public void registerTeam(ITabPlayer viewer) {
		Property tagprefix = getProperty("tagprefix");
		Property tagsuffix = getProperty("tagsuffix");
		String replacedPrefix = tagprefix.getFormat(viewer);
		String replacedSuffix = tagsuffix.getFormat(viewer);
		PacketAPI.registerScoreboardTeam(viewer, teamName, replacedPrefix, replacedSuffix, getTeamVisibility(), getTeamPush(), Arrays.asList(getName()), null);
	}

	public void unregisterTeam() {
		if (teamName == null) return;
		Object packet = PacketPlayOutScoreboardTeam.REMOVE_TEAM(teamName).setTeamOptions(69).create(ProtocolVersion.SERVER_VERSION);
		for (ITabPlayer viewer : Shared.getPlayers()) {
			viewer.sendPacket(packet);
		}
	}

	public void unregisterTeam(ITabPlayer viewer) {
		viewer.sendCustomPacket(PacketPlayOutScoreboardTeam.REMOVE_TEAM(teamName).setTeamOptions(69));
	}

	public void updateDisabledWorlds(String world) {
		disabledHeaderFooter = isDisabledWorld(Configs.disabledHeaderFooter, world);
		disabledTablistNames = isDisabledWorld(Configs.disabledTablistNames, world);
		disabledNametag = isDisabledWorld(Configs.disabledNametag, world);
		disabledTablistObjective = isDisabledWorld(Configs.disabledTablistObjective, world);
		disabledBossbar = isDisabledWorld(Configs.disabledBossbar, world);
		disabledBelowname = isDisabledWorld(Configs.disabledBelowname, world);
	}
	public boolean isDisabledWorld(List<String> disabledWorlds, String world) {
		if (disabledWorlds == null) return false;
		if (disabledWorlds.contains("WHITELIST")) {
			for (String enabled : disabledWorlds) {
				if (enabled != null && enabled.equals(world)) return false;
			}
			return true;
		} else {
			for (String disabled : disabledWorlds) {
				if (disabled != null && disabled.equals(world)) return true;
			}
			return false;
		}
	}
	
	public ArmorStandManager getArmorStandManager() {
		return armorStandManager;
	}

	public void setArmorStandManager(ArmorStandManager armorStandManager) {
		this.armorStandManager = armorStandManager;
	}
	
	
	/*
	 *  Implementing interface
	 */
	
	
	@Override
	public void setValueTemporarily(EnumProperty type, String value) {
		Placeholders.checkForRegistration(value);
		getProperty(type.toString()).setTemporaryValue(value);
		if (Shared.featureManager.isFeatureEnabled("nametagx") && type.toString().contains("tag")) {
			setProperty("nametag",getProperty("tagprefix").getCurrentRawValue() + getProperty("customtagname").getCurrentRawValue() + getProperty("tagsuffix").getCurrentRawValue(), null);
		}
		forceRefresh();
	}
	
	@Override
	public void setValuePermanently(EnumProperty type, String value) {
		Placeholders.checkForRegistration(value);
		getProperty(type.toString()).changeRawValue(value);
		((PlayerCommand)Shared.command.subcommands.get("player")).savePlayer(null, getName(), type.toString(), value);
		if (Shared.featureManager.isFeatureEnabled("nametagx") && type.toString().contains("tag")) {
			setProperty("nametag", getProperty("tagprefix").getCurrentRawValue() + getProperty("customtagname").getCurrentRawValue() + getProperty("tagsuffix").getCurrentRawValue(), null);
		}
		forceRefresh();
	}
	
	@Override
	public String getTemporaryValue(EnumProperty type) {
		return getProperty(type.toString()).getTemporaryValue();
	}
	
	@Override
	public boolean hasTemporaryValue(EnumProperty type) {
		return getTemporaryValue(type) != null;
	}
	
	@Override
	public void removeTemporaryValue(EnumProperty type) {
		setValueTemporarily(type, null);
	}
	
	@Override
	public String getOriginalValue(EnumProperty type) {
		return getProperty(type.toString()).getOriginalRawValue();
	}
	
	@Override
	public void sendHeaderFooter(String header, String footer) {
		sendCustomPacket(new PacketPlayOutPlayerListHeaderFooter(header, footer));
	}
	
	@Override
	public void hideNametag() {
		hiddenNametag = true;
		updateTeamData();
	}
	
	@Override
	public void showNametag() {
		hiddenNametag = false;
		updateTeamData();
	}
	
	@Override
	public boolean hasHiddenNametag() {
		return hiddenNametag;
	}
	
	@Override
	public void forceRefresh() {
		Shared.featureManager.refresh(this, true);
	}
	
	@Override
	public void showScoreboard(me.neznamy.tab.api.Scoreboard scoreboard) {
		if (forcedScoreboard != null) {
			forcedScoreboard.unregister(this);
		}
		if (activeScoreboard != null) {
			activeScoreboard.unregister(this);
			activeScoreboard = null;
		}
		forcedScoreboard = (Scoreboard) scoreboard;
		((Scoreboard)scoreboard).register(this);
	}
	
	@Override
	public void showScoreboard(String name) {
		ScoreboardManager sbm = ((ScoreboardManager) Shared.featureManager.getFeature("scoreboard"));
		if (sbm == null) throw new IllegalStateException("Scoreboard feature is not enabled");
		Scoreboard scoreboard = sbm.getScoreboards().get(name);
		if (scoreboard == null) throw new IllegalArgumentException("No scoreboard found with name: " + name);
		showScoreboard(scoreboard);
	}
	
	@Override
	public void removeCustomScoreboard() {
		if (forcedScoreboard == null) return;
		ScoreboardManager sbm = ((ScoreboardManager) Shared.featureManager.getFeature("scoreboard"));
		if (sbm == null) throw new IllegalStateException("Scoreboard feature is not enabled");
		Scoreboard sb = sbm.getScoreboards().get(sbm.getHighestScoreboard(this));
		activeScoreboard = sb;
		sb.register(this);
		forcedScoreboard = null;
	}
	
	@Override
	public ProtocolVersion getVersion() {
		return version;
	}
	
	@Override
	public org.bukkit.entity.Player getBukkitEntity() {
		throw new IllegalStateException("Wrong platform");
	}

	@Override
	public ProxiedPlayer getBungeeEntity() {
		throw new IllegalStateException("Wrong platform");
	}

	@Override
	public com.velocitypowered.api.proxy.Player getVelocityEntity() {
		throw new IllegalStateException("Wrong platform");
	}
	
	@Override
	public void sendCustomBukkitPacket(PacketPlayOut packet) {
		try {
			sendPacket(packet.toNMS(getVersion()));
		} catch (InvocationTargetException e) {
			Shared.errorManager.printError("An error occurred when creating " + packet.getClass().getSimpleName(), e.getTargetException());
		} catch (Throwable e) {
			Shared.errorManager.printError("An error occurred when creating " + packet.getClass().getSimpleName(), e);
		}
	}
	
	@Override
	public String getWorldName() {
		return world;
	}
	
	@Override
	public void sendCustomPacket(UniversalPacketPlayOut packet) {
		sendPacket(packet.create(getVersion()));
	}
	
	@Override
	public Property getProperty(String name) {
		return properties.get(name);
	}
	
	@Override
	public String getGroup() {
		return permissionGroup;
	}
	
	@Override
	public void toggleNametagPreview() {
		if (armorStandManager == null) throw new IllegalStateException("Unlimited nametag mode is not enabled");
		if (previewingNametag) {
			armorStandManager.destroy(this);
			sendMessage(Configs.preview_off, true);
		} else {
			armorStandManager.spawn(this);
			sendMessage(Configs.preview_on, true);
		}
		previewingNametag = !previewingNametag;
	}
	
	@Override
	public boolean isPreviewingNametag() {
		return previewingNametag;
	}
}
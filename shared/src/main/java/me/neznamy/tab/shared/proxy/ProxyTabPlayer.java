package me.neznamy.tab.shared.proxy;

import com.google.common.collect.Lists;
import me.neznamy.tab.shared.ITabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.permission.VaultBridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract class for player on proxy containing variables and methods
 * shared between proxies.
 */
public abstract class ProxyTabPlayer extends ITabPlayer {

	/** Player's vanish status from backend server */
	private boolean vanished;

	/** Player's disguise status from backend server */
	private boolean disguised;

	/** Player's invisibility potion status from backend server */
	private boolean invisible;

	/** Map of player's requested permissions */
	private final Map<String, Boolean> permissions = new HashMap<>();

	/**
	 * Constructs new instance with given parameters and sends a message
	 * to bridge about this player joining with join data
	 *
	 * @param	player
	 * 			platform-specific player object
	 * @param	uniqueId
	 * 			Player's unique ID
	 * @param	name
	 * 			Player's name
	 * @param	server
	 * 			Player's server
	 * @param	protocolVersion
	 * 			Player's protocol network id
	 */
	protected ProxyTabPlayer(Object player, UUID uniqueId, String name, String server, int protocolVersion) {
		super(player, uniqueId, name, server, "N/A", protocolVersion);
		List<Object> args = Lists.newArrayList("PlayerJoin", TAB.getInstance().getGroupManager().getPlugin() instanceof VaultBridge,
				TAB.getInstance().getFeatureManager().isFeatureEnabled(TabConstants.Feature.PET_FIX));
		ProxyPlatform platform = (ProxyPlatform) TAB.getInstance().getPlatform();
		Map<String, Integer> placeholders = platform.getBridgePlaceholders();
		args.add(placeholders.size());
		for (Map.Entry<String, Integer> entry : placeholders.entrySet()) {
			args.add(entry.getKey());
			args.add(entry.getValue());
		}
		((ProxyPlatform)TAB.getInstance().getPlatform()).getPluginMessageHandler().sendMessage(this, args.toArray());
	}

	/**
	 * Sets vanish status to provided value
	 *
	 * @param	vanished
	 * 			new vanish status
	 */
	public void setVanished(boolean vanished) {
		this.vanished = vanished;
	}

	/**
	 * Sets disguise status to provided value
	 *
	 * @param	disguised
	 * 			new disguise status
	 */
	public void setDisguised(boolean disguised) {
		this.disguised = disguised;
	}

	/**
	 * Sets invisibility status to provided value
	 *
	 * @param	invisible
	 * 			new invisibility status
	 */
	public void setInvisible(boolean invisible) {
		this.invisible = invisible;
	}

	/**
	 * Sets permission presence status to provided value
	 *
	 * @param	permission
	 * 			Requested permission node
	 * @param	value
	 * 			Permission value
	 */
	public void setHasPermission(String permission, boolean value) {
		permissions.put(permission, value);
	}

	/**
	 * Performs platform-specific permission check and returns the result
	 *
	 * @param	permission
	 * 			Permission to check for
	 * @return	Result from hasPermission call
	 */
	public abstract boolean hasPermission0(String permission);

	@Override
	public boolean isVanished() {
		return vanished;
	}

	@Override
	public boolean isDisguised() {
		return disguised;
	}

	@Override
	public boolean hasInvisibilityPotion() {
		return invisible;
	}

	@Override
	public boolean hasPermission(String permission) {
		if (TAB.getInstance().getConfiguration().isBukkitPermissions()) {
			((ProxyPlatform)TAB.getInstance().getPlatform()).getPluginMessageHandler().sendMessage(this, "Permission", permission);
			return permissions.getOrDefault(permission, false);
		}
		return hasPermission0(permission);
	}
}
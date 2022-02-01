package me.neznamy.tab.shared.features;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.placeholder.Placeholder;
import me.neznamy.tab.api.placeholder.PlayerPlaceholder;
import me.neznamy.tab.api.placeholder.RelationalPlaceholder;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.permission.VaultBridge;
import me.neznamy.tab.shared.placeholders.PlayerPlaceholderImpl;
import me.neznamy.tab.shared.proxy.ProxyTabPlayer;

/**
 * Universal interface for proxy to manage plugin messages
 */
public abstract class PluginMessageHandler {

	/**
	 * Handles incoming plugin message with tab's channel name
	 *
	 * @param	player
	 * 			plugin message receiver
	 * @param	in
	 * 			incoming message
	 */
	public void onPluginMessage(ProxyTabPlayer player, ByteArrayDataInput in) {
		if (TAB.getInstance().isDisabled()) return; //reload in progress
		String subChannel = in.readUTF();
		if ("Placeholder".equals(subChannel)){
			Placeholder placeholder = TAB.getInstance().getPlaceholderManager().getPlaceholder(in.readUTF());
			if (placeholder instanceof RelationalPlaceholder) {
				((RelationalPlaceholder)placeholder).updateValue(player, TAB.getInstance().getPlayer(in.readUTF()), in.readUTF());
			} else {
				((PlayerPlaceholder)placeholder).updateValue(player, in.readUTF());
			}
		}
		if ("Vanished".equals(subChannel)) {
			player.setVanished(in.readBoolean());
			((PlayerPlaceholderImpl) TAB.getInstance().getPlaceholderManager().getPlaceholder("%vanished%")).updateValue(player, player.isVanished());
		}
		if ("Disguised".equals(subChannel)) {
			player.setDisguised(in.readBoolean());
		}
		if ("Invisible".equals(subChannel)) {
			player.setInvisible(in.readBoolean());
		}
		if ("World".equals(subChannel)) {
			TAB.getInstance().getFeatureManager().onWorldChange(player.getUniqueId(), in.readUTF());
		}
		if ("Group".equals(subChannel)) {
			player.setGroup(in.readUTF());
		}
		if ("Permission".equals(subChannel)) {
			player.setHasPermission(in.readUTF(), in.readBoolean());
		}
		if ("PlayerJoinResponse".equals(subChannel)) {
			player.setVanished(in.readBoolean());
			player.setDisguised(in.readBoolean());
			player.setInvisible(in.readBoolean());
			TAB.getInstance().getFeatureManager().onWorldChange(player.getUniqueId(), in.readUTF());
			if (TAB.getInstance().getGroupManager().getPlugin() instanceof VaultBridge) player.setGroup(in.readUTF());
			int placeholderCount = in.readInt();
			for (int i=0; i<placeholderCount; i++) {
				String identifier = in.readUTF();
				if (identifier.startsWith("%rel_")) {
					int playerCount = in.readInt();
					for (int j=0; j<playerCount; j++) {
						((RelationalPlaceholder)TAB.getInstance().getPlaceholderManager().getPlaceholder(identifier))
								.updateValue(player, TAB.getInstance().getPlayer(in.readUTF()), in.readUTF());
					}
				} else {
					((PlayerPlaceholder)TAB.getInstance().getPlaceholderManager().getPlaceholder(identifier)).updateValue(player, in.readUTF());
				}
			}
		}
	}

	/**
	 * Sends plugin message to specified player
	 *
	 * @param	player
	 * 			Player to send plugin message to
	 * @param	args
	 * 			Messages to encode
	 */
	public void sendMessage(TabPlayer player, Object... args) {
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		for (Object arg : args) {
			writeObject(out, arg);
		}
		sendPluginMessage(player, out.toByteArray());
	}

	/**
	 * Writes object to data input by calling proper write method
	 * based on data type of the object.
	 *
	 * @param	out
	 * 			Data output to write to
	 * @param	value
	 * 			Value to write
	 */
	private void writeObject(ByteArrayDataOutput out, Object value) {
		if (value == null) return;
		if (value instanceof String) {
			out.writeUTF((String) value);
		} else if (value instanceof Boolean) {
			out.writeBoolean((boolean) value);
		} else if (value instanceof Integer) {
			out.writeInt((int) value);
		} else throw new IllegalArgumentException("Unhandled message data type " + value.getClass().getName());
	}

	/**
	 * Sends plugin message to specified player
	 *
	 * @param	player
	 * 			Player to go through
	 * @param	message
	 * 			Encoded message
	 */
	public abstract void sendPluginMessage(TabPlayer player, byte[] message);
}

package me.neznamy.tab.platforms.velocity.v3_0_0;

import com.velocitypowered.proxy.protocol.packet.ScoreboardDisplay;
import com.velocitypowered.proxy.protocol.packet.ScoreboardObjective;
import com.velocitypowered.proxy.protocol.packet.ScoreboardScore;
import com.velocitypowered.proxy.protocol.packet.Team;
import me.neznamy.tab.shared.ProtocolVersion;
import me.neznamy.tab.shared.packets.*;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.PlayerInfoData;
import me.neznamy.tab.shared.packets.PacketPlayOutScoreboardObjective.EnumScoreboardHealthDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Packet builder for Velocity platform
 */
public class VelocityPacketBuilder implements PacketBuilder {

	@Override
	public Object build(PacketPlayOutBoss packet, ProtocolVersion clientVersion) {
		return packet;
	}

	@Override
	public Object build(PacketPlayOutChat packet, ProtocolVersion clientVersion) {
		return packet;
	}

	@Override
	public Object build(PacketPlayOutPlayerInfo packet, ProtocolVersion clientVersion) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
		List<Object> items = new ArrayList<>();
		for (PlayerInfoData data : packet.getEntries()) {
			Object item = Class.forName("com.velocitypowered.proxy.protocol.packet.PlayerListItem$Item").getConstructor(UUID.class).newInstance(data.getUniqueId());
			item.getClass().getMethod("setDisplayName", Component.class).invoke(item, data.getDisplayName() == null ? null : Main.stringToComponent(data.getDisplayName().toString(clientVersion)));
			if (data.getGameMode() != null) {
				item.getClass().getMethod("setGameMode", int.class).invoke(item, data.getGameMode().ordinal()-1);
			}
			item.getClass().getMethod("setLatency", int.class).invoke(item, data.getLatency());
			if (data.getSkin() != null) item.getClass().getMethod("setProperties", List.class).invoke(item, data.getSkin());
			item.getClass().getMethod("setName", String.class).invoke(item, data.getName());
			items.add(item);
		}
		return Class.forName("com.velocitypowered.proxy.protocol.packet.PlayerListItem").getConstructor(int.class, List.class).newInstance(packet.getAction().ordinal(), items);
	}

	@Override
	public Object build(PacketPlayOutPlayerListHeaderFooter packet, ProtocolVersion clientVersion) {
		return packet;
	}

	@Override
	public Object build(PacketPlayOutScoreboardDisplayObjective packet, ProtocolVersion clientVersion) {
		return new ScoreboardDisplay((byte)packet.getSlot(), packet.getObjectiveName());
	}

	@Override
	public Object build(PacketPlayOutScoreboardObjective packet, ProtocolVersion clientVersion) {
		return new ScoreboardObjective(packet.getObjectiveName(), jsonOrCut(packet.getDisplayName(), clientVersion, 32),
				packet.getRenderType() == null ? null : ScoreboardObjective.HealthDisplay.valueOf(packet.getRenderType().toString()), (byte)packet.getMethod());
	}

	@Override
	public Object build(PacketPlayOutScoreboardScore packet, ProtocolVersion clientVersion) {
		return new ScoreboardScore(packet.getPlayer(), (byte) packet.getAction().ordinal(), packet.getObjectiveName(), packet.getScore());
	}

	@Override
	public Object build(PacketPlayOutScoreboardTeam packet, ProtocolVersion clientVersion) {
		int color = 0;
		if (clientVersion.getMinorVersion() >= 13) {
			color = (packet.getColor() != null ? packet.getColor() : EnumChatFormat.lastColorsOf(packet.getPlayerPrefix())).getNetworkId();
		}
		return new Team(packet.getName(), (byte)packet.getMethod(), jsonOrCut(packet.getName(), clientVersion, 16), jsonOrCut(packet.getPlayerPrefix(),
				clientVersion, 16), jsonOrCut(packet.getPlayerSuffix(), clientVersion, 16), packet.getNametagVisibility(),
				packet.getCollisionRule(), color, (byte)packet.getOptions(), packet.getPlayers().toArray(new String[0]));
	}

	@Override
	public PacketPlayOutPlayerInfo readPlayerInfo(Object packet, ProtocolVersion clientVersion) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		List<PlayerInfoData> listData = new ArrayList<>();
		for (Object item : (List<Object>) packet.getClass().getMethod("getItems").invoke(packet)) {
			Component displayNameComponent = (Component) item.getClass().getMethod("getDisplayName").invoke(item);
			String displayName = displayNameComponent == null ? null : GsonComponentSerializer.gson().serialize(displayNameComponent);
			listData.add(new PlayerInfoData(
							(String) item.getClass().getMethod("getName").invoke(item),
							(UUID) item.getClass().getMethod("getUuid").invoke(item),
							item.getClass().getMethod("getProperties").invoke(item),
							(int) item.getClass().getMethod("getLatency").invoke(item),
							PacketPlayOutPlayerInfo.EnumGamemode.values()[(int)item.getClass().getMethod("getGameMode").invoke(item)+1],
							displayName == null ? null : IChatBaseComponent.fromString(displayName)
					)
			);
		}
		return new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.values()[(int) (packet.getClass().getMethod("getAction").invoke(packet))], listData);
	}

	@Override
	public PacketPlayOutScoreboardObjective readObjective(Object packet, ProtocolVersion clientVersion) {
		ScoreboardObjective newPacket = (ScoreboardObjective) packet;
		String title;
		if (clientVersion.getMinorVersion() >= 13) {
			title = newPacket.value == null ? null : IChatBaseComponent.fromString(newPacket.value).toLegacyText();
		} else {
			title = newPacket.value;
		}
		EnumScoreboardHealthDisplay renderType = (newPacket.type == null ? null : EnumScoreboardHealthDisplay.valueOf(newPacket.type.toString().toUpperCase()));
		return new PacketPlayOutScoreboardObjective(newPacket.action, newPacket.name, title, renderType);
	}

	@Override
	public PacketPlayOutScoreboardDisplayObjective readDisplayObjective(Object packet, ProtocolVersion clientVersion) {
		return new PacketPlayOutScoreboardDisplayObjective(((ScoreboardDisplay) packet).position, ((ScoreboardDisplay) packet).name);
	}
}
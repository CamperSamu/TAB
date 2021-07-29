package me.neznamy.tab.platforms.velocity;

import java.util.List;

import com.google.common.collect.Lists;
import com.velocitypowered.proxy.protocol.packet.ScoreboardTeam;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.cpu.UsageType;
import me.neznamy.tab.shared.features.PipelineInjector;

public class VelocityPipelineInjector extends PipelineInjector {

    //handler to inject before
    private final String INJECT_POSITION = "handler";

    /**
     * Constructs new instance with given parameter
     * @param tab - tab instance
     */
    public VelocityPipelineInjector(TAB tab) {
        super(tab);
    }

    @Override
    public void inject(TabPlayer player) {
        if (player.getVersion().getMinorVersion() < 8) return;
        uninject(player);
        player.getChannel().pipeline().addBefore(INJECT_POSITION, DECODER_NAME, new VelocityChannelDuplexHandler(player));
    }

    @Override
    public void uninject(TabPlayer player) {
        if (player.getVersion().getMinorVersion() < 8) return;
        if (player.getChannel().pipeline().names().contains(DECODER_NAME)) player.getChannel().pipeline().remove(DECODER_NAME);
    }

    /**
     * Custom channel duplex handler override
     */
    public class VelocityChannelDuplexHandler extends ChannelDuplexHandler {

        //injected player
        private TabPlayer player;

        /**
         * Constructs new instance with given player
         * @param player - player to inject
         */
        public VelocityChannelDuplexHandler(TabPlayer player) {
            this.player = player;
        }

        @Override
        public void write(ChannelHandlerContext context, Object packet, ChannelPromise channelPromise) throws Exception {
            try {
                switch (packet.getClass().getSimpleName()) {
                    case "PlayerListItem":
                        super.write(context, tab.getFeatureManager().onPacketPlayOutPlayerInfo(player, packet), channelPromise);
                        return;
                    case "ScoreboardTeam":
                        if (tab.getFeatureManager().isFeatureEnabled("nametag16") && antiOverrideTeams) {
                            modifyPlayers((ScoreboardTeam) packet);
                        }
                        break;
                    case "JoinGame":
                        //making sure to not send own packets before join packet is actually sent
                        super.write(context, packet, channelPromise);
                        tab.getFeatureManager().onLoginPacket(player);
                        return;
                    case "ScoreboardDisplay":
                        if (antiOverrideObjectives && tab.getFeatureManager().onDisplayObjective(player, packet)) return;
                        break;
                    case "ScoreboardObjective":
                        if (antiOverrideObjectives) tab.getFeatureManager().onObjective(player, packet);
                        break;
                    default:
                        break;
                }
            } catch (Throwable e){
                tab.getErrorManager().printError("An error occurred when analyzing packets for player " + player.getName() + " with client version " + player.getVersion().getFriendlyName(), e);
            }
            super.write(context, packet, channelPromise);
        }

        /**
         * Removes all real players from packet if the packet doesn't come from TAB
         * @param packet - packet to modify
         */
        private void modifyPlayers(ScoreboardTeam packet) {
            long time = System.nanoTime();
            if (packet.getPlayers() == null) return;
            List<String> col = Lists.newArrayList(packet.getPlayers());
            for (TabPlayer p : tab.getPlayers()) {
                if (col.contains(p.getName()) && !tab.getFeatureManager().getNameTagFeature().getPlayersInDisabledWorlds().contains(p)
                    && !p.hasTeamHandlingPaused() && !packet.getName().equals(p.getTeamName())) {
                    logTeamOverride(packet.getName(), p.getName());
                    col.remove(p.getName());
                }
            }
            packet.setPlayers(col);
            tab.getCPUManager().addTime(TabFeature.NAMETAGS, UsageType.ANTI_OVERRIDE, System.nanoTime()-time);
        }
    }
}
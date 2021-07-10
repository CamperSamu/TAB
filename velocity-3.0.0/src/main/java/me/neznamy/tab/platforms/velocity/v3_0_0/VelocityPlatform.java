package me.neznamy.tab.platforms.velocity.v3_0_0;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.features.*;
import me.neznamy.tab.shared.features.scoreboard.ScoreboardManager;
import me.neznamy.tab.shared.permission.LuckPerms;
import me.neznamy.tab.shared.permission.PermissionPlugin;
import me.neznamy.tab.shared.permission.VaultBridge;
import me.neznamy.tab.shared.placeholders.UniversalPlaceholderRegistry;
import me.neznamy.tab.shared.proxy.ProxyPlatform;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

/**
 * Velocity implementation of Platform
 */
public class VelocityPlatform extends ProxyPlatform {

	//instance of proxyserver
	private ProxyServer server;
	
	/**
	 * Constructs new instance with given parameter
	 * @param server - instance of proxyserver
	 */
	public VelocityPlatform(ProxyServer server, PluginMessageHandler plm) {
		super(plm);
		this.server = server;
	}
	
	@Override
	public PermissionPlugin detectPermissionPlugin() {
		Optional<PluginContainer> luckperms = server.getPluginManager().getPlugin("luckperms");
		if (TAB.getInstance().getConfiguration().isBukkitPermissions()) {
			return new VaultBridge(plm);
		} else if (luckperms.isPresent()) {
			return new LuckPerms(luckperms.get().getDescription().getVersion().get());
		} else {
			return new VaultBridge(plm);
		}
	}
	
	@Override
	public void loadFeatures() {
		TAB tab = TAB.getInstance();
		tab.getPlaceholderManager().addRegistry(new VelocityPlaceholderRegistry(server));
		tab.getPlaceholderManager().addRegistry(new UniversalPlaceholderRegistry());
		tab.getPlaceholderManager().registerPlaceholders();
		if (tab.getConfiguration().isPipelineInjection()) tab.getFeatureManager().registerFeature("injection", new VelocityPipelineInjector(tab));
		if (tab.getConfiguration().getConfig().getBoolean("change-nametag-prefix-suffix", true)) tab.getFeatureManager().registerFeature("nametag16", new NameTag(tab));
		loadUniversalFeatures();
		if (tab.getConfiguration().getConfig().getBoolean("ping-spoof.enabled", false)) tab.getFeatureManager().registerFeature("pingspoof", new PingSpoof());
		if (tab.getConfiguration().getConfig().getString("yellow-number-in-tablist", "%ping%").length() > 0) tab.getFeatureManager().registerFeature("tabobjective", new TabObjective(tab));
		if (tab.getConfiguration().getConfig().getBoolean("do-not-move-spectators", false)) tab.getFeatureManager().registerFeature("spectatorfix", new SpectatorFix());
		if (tab.getConfiguration().getConfig().getBoolean("classic-vanilla-belowname.enabled", true)) tab.getFeatureManager().registerFeature("belowname", new BelowName(tab));
		if (tab.getConfiguration().getPremiumConfig() != null && tab.getConfiguration().getPremiumConfig().getBoolean("scoreboard.enabled", false)) tab.getFeatureManager().registerFeature("scoreboard", new ScoreboardManager(tab));
		if (tab.getConfiguration().getConfig().getBoolean("global-playerlist.enabled", false)) 	tab.getFeatureManager().registerFeature("globalplayerlist", new GlobalPlayerlist(tab));
		for (Player p : server.getAllPlayers()) {
			try {
				tab.addPlayer(new VelocityTabPlayer(p, plm));
			} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void sendConsoleMessage(String message, boolean translateColors) {
		server.getConsoleCommandSource().sendMessage(Identity.nil(), Component.text(translateColors ? message.replace('&', '\u00a7') : message));
	}
	
	@Override
	public String getServerVersion() {
		return server.getVersion().getName() + " v" + server.getVersion().getVersion();
	}

	@Override
	public String getSeparatorType() {
		return "server";
	}

	@Override
	public File getDataFolder() {
		return new File("plugins" + File.separatorChar + "TAB");
	}

	@Override
	public void callLoadEvent() {
		//not needed here
	}

	@Override
	public int getMaxPlayers() {
		return server.getConfiguration().getShowMaxPlayers();
	}
	
	@Override
	public String getConfigName() {
		return "velocityconfig.yml";
	}
}
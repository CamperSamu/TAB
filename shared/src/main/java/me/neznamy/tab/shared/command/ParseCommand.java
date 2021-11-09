package me.neznamy.tab.shared.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.chat.EnumChatFormat;
import me.neznamy.tab.api.chat.IChatBaseComponent;
import me.neznamy.tab.shared.PropertyImpl;
import me.neznamy.tab.shared.TAB;

/**
 * Handler for "/tab parse <player> <placeholder>" subcommand
 */
public class ParseCommand extends SubCommand {

	/**
	 * Constructs new instance
	 */
	public ParseCommand() {
		super("parse", "tab.parse");
	}

	@Override
	public void execute(TabPlayer sender, String[] args) {
		if (args.length < 2) {
			sendMessage(sender, "Usage: /tab parse <player> <placeholder>");
			return;
		}
		TabPlayer target;
		if (args[0].equals("me") && sender != null) {
			target = sender;
		} else {
			target = TAB.getInstance().getPlayer(args[0]);
			if (target == null) {
				sendMessage(sender, getTranslation("player_not_found"));
				return;
			}
		}
		String replaced = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		String message = EnumChatFormat.color("&6Replacing placeholder &e%placeholder% &6for player &e" + target.getName()).replace("%placeholder%", replaced);
		sendRawMessage(sender, message);
		try {
			replaced = new PropertyImpl(null, target, replaced).get();
		} catch (Exception e) {
			sendMessage(sender, "&cThe placeholder threw an exception when parsing. Check console for more info.");
			TAB.getInstance().getErrorManager().printError("", e, true);
			return;
		}
		IChatBaseComponent colored = IChatBaseComponent.optimizedComponent("With colors: " + replaced);
		if (sender != null) {
			sender.sendMessage(colored);
		} else {
			sendRawMessage(sender, colored.toLegacyText());
		}
		sendRawMessage(sender, "Without colors: " + EnumChatFormat.decolor(replaced));
	}
	
	@Override
	public List<String> complete(TabPlayer sender, String[] arguments) {
		return arguments.length == 1 ? getOnlinePlayers(arguments[0]) : new ArrayList<>();
	}
}
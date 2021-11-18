package me.neznamy.tab.shared.placeholders;

import java.util.ArrayList;
import java.util.List;

import me.neznamy.tab.api.chat.EnumChatFormat;
import me.neznamy.tab.api.chat.rgb.RGBUtils;
import me.neznamy.tab.shared.TAB;

/**
 * A class representing an animation from animations.yml
 */
public class Animation {
	
	//name of the animations
	private final String name;
	
	//all defined messages
	private final String[] messages;
	
	//change interval
	private final int interval;
	
	//all nested placeholders used in animation frames
	private final String[] nestedPlaceholders;
	
	/**
	 * Constructs new instance with given arguments which are fixed if necessary, such as when
	 * refresh is not divisible by 50
	 * @param name - animations name
	 * @param list - list of animation framrs
	 * @param interval - refresh interval to next frame
	 */
	public Animation(String name, List<String> list, int interval){
		this.name = name;
		this.interval = TAB.getInstance().getErrorManager().fixAnimationInterval(name, interval);
		this.messages = TAB.getInstance().getErrorManager().fixAnimationFrames(name, list).toArray(new String[0]);
		List<String> nestedPlaceholders0 = new ArrayList<>();
		for (int i=0; i<messages.length; i++) {
			messages[i] = RGBUtils.getInstance().applyFormats(messages[i], true);
			messages[i] = EnumChatFormat.color(messages[i]);
			nestedPlaceholders0.addAll(TAB.getInstance().getPlaceholderManager().detectPlaceholders(messages[i]));
		}
		TAB.getInstance().getPlaceholderManager().addUsedPlaceholders(nestedPlaceholders0);
		nestedPlaceholders = nestedPlaceholders0.toArray(new String[0]);
	}
	
	/**
	 * Returns all messages
	 * @return all messages
	 */
	public String[] getAllMessages() {
		return messages;
	}
	
	/**
	 * Current message depending on current system time
	 * @return current message
	 */
	public String getMessage(){
		return messages[(int) ((System.currentTimeMillis()%(messages.length*interval))/interval)];
	}
	
	/**
	 * Returns animation's name
	 * @return animation's name
	 */
	public String getName(){
		return name;
	}
	
	/**
	 * Returns change interval defined in animations.yml
	 * @return change interval
	 */
	public int getInterval() {
		return interval;
	}

	/**
	 * Returns array of all placeholders used in all frames
	 * @return array of all placeholders used in all frames
	 */
	public String[] getNestedPlaceholders() {
		return nestedPlaceholders;
	}
}
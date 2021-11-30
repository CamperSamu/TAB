package me.neznamy.tab.api.chat;

import me.neznamy.tab.api.chat.rgb.RGBUtils;

/**
 * A class representing the n.m.s.EnumChatFormat class to make work with it much easier
 */
public enum EnumChatFormat {

	BLACK(0, '0', "000000"),
	DARK_BLUE(1, '1', "0000AA"),
	DARK_GREEN(2, '2', "00AA00"),
	DARK_AQUA(3, '3', "00AAAA"),
	DARK_RED(4, '4', "AA0000"),
	DARK_PURPLE(5, '5', "AA00AA"),
	GOLD(6, '6', "FFAA00"),
	GRAY(7, '7', "AAAAAA"),
	DARK_GRAY(8, '8', "555555"),
	BLUE(9, '9', "5555FF"),
	GREEN(10, 'a', "55FF55"),
	AQUA(11, 'b', "55FFFF"),
	RED(12, 'c', "FF5555"),
	LIGHT_PURPLE(13, 'd', "FF55FF"),
	YELLOW(14, 'e', "FFFF55"),
	WHITE(15, 'f', "FFFFFF"),
	OBFUSCATED(16, 'k'),
	BOLD(17, 'l'),
	STRIKETHROUGH(18, 'm'),
	UNDERLINE(19, 'n'),
	ITALIC(20, 'o'),
	RESET(21, 'r');

	public static final char COLOR_CHAR = 0x00a7;
	public static final String COLOR_STRING = String.valueOf(COLOR_CHAR);

	//network id of the color
	private final int networkId;
	
	//characer representing the color
	private final char character;
	
	//red value of this color
	private final int red;
	
	//green value of this color
	private final int green;
	
	//blue value of this color
	private final int blue;
	
	//hex code as string
	private final String hexCode;
	
	//color char followed by color's character
	private final String chatFormat;

	/**
	 * Constructs new instance with given parameters
	 * @param networkId - network id of the color
	 * @param character - character representing the color
	 * @param hexColor - hex code of the color
	 */
	private EnumChatFormat(int networkId, char character, String hexCode) {
		this.networkId = networkId;
		this.character = character;
		this.chatFormat = String.valueOf(COLOR_CHAR) + character;
		this.hexCode = hexCode;
		int hexColor = Integer.parseInt(hexCode, 16);
		red = (hexColor >> 16) & 0xFF;
		green = (hexColor >> 8) & 0xFF;
		blue = hexColor & 0xFF;
	}
	
	/**
	 * Constructs new instance with given parameters
	 * @param networkId - network id of the color
	 * @param character - character representing the color
	 */
	private EnumChatFormat(int networkId, char character) {
		this.networkId = networkId;
		this.character = character;
		this.chatFormat = String.valueOf(COLOR_CHAR) + character;
		red = 0;
		green = 0;
		blue = 0;
		hexCode = null;
	}
	
	/**
	 * Returns network id of this color
	 * @return network if of this color
	 */
	public int getNetworkId() {
		return networkId;
	}
	
	/**
	 * Returns red value of this color
	 * @return red value
	 */
	public int getRed() {
		return red;
	}
	
	/**
	 * Returns green value of this color
	 * @return green value
	 */
	public int getGreen() {
		return green;
	}
	
	/**
	 * Returns blue value of this color
	 * @return blue value
	 */
	public int getBlue() {
		return blue;
	}
	
	/**
	 * Returns enum value based on inserted color or null if character is not valid
	 * @param c - color code character (0-9, a-f, k-o, r)
	 * @return instance from the character
	 */
	public static EnumChatFormat getByChar(char c) {
		for (EnumChatFormat format : values()) {
			if (format.character == c) return format;
		}
		return null;
	}
	
	/**
	 * Returns enum value of last colors used in given string
	 * @param string - string to check last colors of
	 * @return last used color code in given string
	 */
	public static EnumChatFormat lastColorsOf(String string) {
		if (string == null || string.length() == 0) return EnumChatFormat.WHITE;
		String legacyText = RGBUtils.getInstance().convertRGBtoLegacy(string); //translating RGB into legacy for nametags
		String last = getLastColors(legacyText);
		if (last.length() > 0) {
			char c = last.toCharArray()[1];
			for (EnumChatFormat e : values()) {
				if (e.character == c) return e;
			}
		}
		return EnumChatFormat.WHITE;
	}
	
	/**
	 * Returns color char followed by color's character
	 * @return color char followed by color's character
	 */
	public String getFormat() {
		return chatFormat;
	}
	
	/**
	 * Returns character representing this color
	 * @return character representing this color
	 */
	public char getCharacter() {
		return character;
	}
	
	/**
	 * Returns hex code of this format, null if this is a magic code
	 * @return hex code of this format
	 */
	public String getHexCode() {
		return hexCode;
	}
	
	/**
	 * Returns enum value with exact red, green and blue values or null if no match
	 * @param red - exact red value
	 * @param green - exact green value
	 * @param blue - exact blue value
	 * @return enum value or null if no such combination exists
	 */
	public static EnumChatFormat fromRGBExact(int red, int green, int blue){
		for (EnumChatFormat format : values()) {
			if (format.red == red && format.green == green && format.blue == blue) return format;
		}
		return null;
	}
	
	//code taken from bukkit, so it can work on bungee too
	public static String color(String textToTranslate){
		if (textToTranslate == null) return null;
		if (!textToTranslate.contains("&")) return textToTranslate;
		char[] b = textToTranslate.toCharArray();
		for (int i = 0; i < b.length - 1; i++) {
			if ((b[i] == '&') && ("0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[(i + 1)]) > -1)){
				b[i] = COLOR_CHAR;
				b[(i + 1)] = Character.toLowerCase(b[(i + 1)]);
			}
		}
		return new String(b);
	}
	
	public static String decolor(String text) {
		if (!text.contains(COLOR_STRING)) return text;
		return text.replace(COLOR_CHAR, '&');
	}
	
	//code taken from bukkit, so it can work on bungee too
	public static String getLastColors(String input) {
		StringBuilder result = new StringBuilder();
		int length = input.length();
		for (int index = length - 1; index > -1; index--){
			char section = input.charAt(index);
			if ((section == COLOR_CHAR || section == '&') && (index < length - 1)){
				char c = input.charAt(index + 1);
				if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRr".contains(String.valueOf(c))) {
					result.insert(0, COLOR_CHAR);
					result.insert(1, c);
					if ("0123456789AaBbCcDdEeFfRr".contains(String.valueOf(c))) {
						break;
					}
				}
			}
		}
		return result.toString();
	}
}
package com.sanlin.mkeyboard;

public class MyConfig {
	private static boolean soundOn;
	private static boolean primeBookOn;
	private static int currentTheme;
	private static boolean showHintLabel;
	private static boolean isDoubleTap;
	

	public MyConfig(boolean soundOn, boolean primeBookOn) {
		MyConfig.soundOn = soundOn;
		MyConfig.primeBookOn = primeBookOn;
	}

	public static boolean isSoundOn() {
		return soundOn;
	}

	public static void setSoundOn(boolean soundOn) {
		MyConfig.soundOn = soundOn;
	}

	public static boolean isPrimeBookOn() {
		return primeBookOn;
	}

	public static void setPrimeBookOn(boolean primeBookOn) {
		MyConfig.primeBookOn = primeBookOn;
	}

	public static int getCurrentTheme() {
		return currentTheme;
	}

	public static void setCurrentTheme(int currentTheme) {
		MyConfig.currentTheme = currentTheme;
	}

	public static boolean isShowHintLabel() {
		return showHintLabel;
	}

	public static void setShowHintLabel(boolean showHintLabel) {
		MyConfig.showHintLabel = showHintLabel;
	}

	public static boolean isDoubleTap() {
		return isDoubleTap;
	}

	public static void setDoubleTap(boolean isDoubleTap) {
		MyConfig.isDoubleTap = isDoubleTap;
	}

}
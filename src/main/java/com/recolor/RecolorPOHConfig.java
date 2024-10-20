package com.recolor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.*;

@ConfigGroup("pohOptions")
public interface RecolorPOHConfig extends Config
{
	@ConfigSection(
			name = "Floor Objects",
			description = "General settings for floor objects",
			position = 0
	)
	String floorSection = "floor";

	@ConfigSection(
			name = "Wall Objects",
			description = "General settings for wall objects",
			position = 1
	)
	String wallSection = "wall";

	@ConfigSection(
			name = "Game Objects",
			description = "General settings for game objects",
			position = 2
	)
	String gameSection = "game";

	// Floor section
	@ConfigItem(
			keyName = "carpetColor",
			name = "Carpet color",
			description = "Color to recolor carpets with",
			position = 0,
			section = floorSection
	)
	default Color carpetColor()
	{
		return new Color(25, 45, 135);
	}

	@ConfigItem(
			keyName = "recolorCarpets",
			name = "Recolor carpets",
			description = "Recolor all carpets",
			position = 1,
			section = floorSection
	)
	default boolean carpetRecolor()
	{
		return false;
	}

	// Object section
	@ConfigItem(
			keyName = "bedColor",
			name = "Bed color",
			description = "Color to recolor beds with",
			position = 0,
			section = gameSection
	)
	default Color bedColor()
	{
		return new Color(25, 45, 135);
	}

	@ConfigItem(
			keyName = "recolorBeds",
			name = "Recolor beds",
			description = "Recolor all beds",
			position = 1,
			section = gameSection
	)
	default boolean bedRecolor()
	{
		return false;
	}

	// Wall section
	@ConfigItem(
			keyName = "curtainColor",
			name = "Curtain color",
			description = "Color to recolor curtains with",
			position = 1,
			section = wallSection
	)
	default Color curtainColor()
	{
		return new Color(25, 45, 135);
	}

	@ConfigItem(
			keyName = "recolorCurtains",
			name = "Recolor curtains",
			description = "Recolor all curtains",
			position = 2,
			section = wallSection
	)
	default boolean curtainRecolor()
	{
		return false;
	}

	@ConfigItem(
			keyName = "otherWallObjectColor",
			name = "Other wall object color",
			description = "Color to recolor other wall objects with",
			position = 3,
			section = wallSection
	)
	default Color otherWallObjectColor()
	{
		return new Color(25, 45, 135);
	}

	@ConfigItem(
			keyName = "recolorOtherWallObjects",
			name = "Recolor other wall objects",
			description = "Recolor all other wall objects",
			position = 4,
			section = wallSection
	)
	default boolean otherWallObjectRecolor()
	{
		return false;
	}
}

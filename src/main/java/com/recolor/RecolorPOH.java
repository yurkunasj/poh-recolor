package com.recolor;

import javax.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.awt.*;
import java.util.*;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "PoH Customization",
		description = "Customizations for your player owned house!",
		conflicts = "117 HD"	// sourced from 'CG Recolor', which this project depends on
)
public class RecolorPOH extends Plugin
{
	// Declarations
	private static final List<Integer> OBJECT_IDS = Arrays.asList(13152, 35965, 35966, 35967, 35968, 35968, 35969, 35970, 35971, 35972, 35972, 35973, 35974, 35975, 35976, 35977, 35978, 35979, 35980, 35992, 35994, 35994, 35995, 35996, 35997, 35998, 35999, 36000, 36001, 36002, 36003, 36004, 36005, 36006, 36007, 36008, 37337);
	private static final List<Integer> GROUND_IDS = Arrays.asList(6762, 6763, 6764, 6765, 6766, 6767, 41125, 41127, 41129);
	private static final List<Integer> DECORATION_IDS = Arrays.asList(6779);
	private static final List<Integer> POH_ZONES = Arrays.asList(7769, 7770, 7513);

	/*
	Testing individual objects
	 */
	private static final List<Integer> CURTAIN_IDS = Arrays.asList(1234, 4321, 6776);
	private ArrayList<DecorativeObject> recordedCurtainObjects = new ArrayList<>();

	private ArrayList<DecorativeObject> recordedDecorativeObjects = new ArrayList<>();
	private ArrayList<GameObject> recordedGameObjects = new ArrayList<>();
	private ArrayList<GroundObject> recordedGroundObjects = new ArrayList<>();
	private ArrayList<Model> recordedModels = new ArrayList<>();
	private ArrayList<Integer> sceneIDs = new ArrayList<>();

	// per object model data processing
	ModelDataProcessor carpetDataProcessor;
	ModelDataProcessor curtainDataProcessor;
	ModelDataProcessor bedDataProcessor;
	ModelDataProcessor otherWallObjectDataProcessor;

	int regionId;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private RecolorPOHConfig config;

	@Provides
	RecolorPOHConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RecolorPOHConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("Recolor started!");

		// Vanilla model facecolors are stored in a .txt -> the new model colors can be calculated before the models even appear making the spawnEvents less expensive
		this.carpetDataProcessor = new ModelDataProcessor("/model_facecolors.txt", "carpet", config.carpetColor(), config.carpetColor(), true);
		this.curtainDataProcessor = new ModelDataProcessor("/model_facecolors.txt", "curtain", config.curtainColor(), config.curtainColor(), true);
		this.otherWallObjectDataProcessor = new ModelDataProcessor("/model_facecolors.txt", "otherWallObject", config.otherWallObjectColor(), config.otherWallObjectColor(), true);
		this.bedDataProcessor = new ModelDataProcessor("/model_facecolors.txt", "bed", config.bedColor(), config.bedColor(), true);

		// set defaults for objects to recolor
		if(config.curtainRecolor()){
			recolor("decorativeObject");
		}
		if(config.otherWallObjectRecolor()) {
			recolor("otherWallObject");
		}
		if(config.bedRecolor()){
			recolor("gameObject");
		}
		if(config.carpetRecolor()) {
			recolor("groundObject");
		}

		// If the user is already logged in AND inside their house, objects still need to be recolored -- aka disable/enable plugin while in PoH
		if(client.getGameState() == GameState.LOGGED_IN) {
			regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
			log.info("Current world point is {}", regionId);
			if(POH_ZONES.contains(regionId)) {
				clientThread.invoke(()-> {
					client.setGameState(GameState.LOADING);
				});
			}
		}
	}

	@Override
	protected void shutDown() {
		log.info("Recolor shutting down");
		clientThread.invoke(() -> {
			clear("all");
			resetSceneIDs();

			//freeing the stored data.
			recordedCurtainObjects.clear();
			recordedGameObjects.clear();
			recordedGroundObjects.clear();
			recordedDecorativeObjects.clear();
			recordedModels.clear();
			sceneIDs.clear();

			if(client.getGameState() == GameState.LOGGED_IN) {
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Some game models may still be affected by the plugin. Please re-log to ensure that everything is properly reset.", null);
				client.setGameState(GameState.LOADING);
			}
		});
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event) {
		log.info("Player spawned");
		log.info("Player location: {}", event.getPlayer().getWorldLocation());
		log.info("Player RegionID: {}", event.getPlayer().getWorldLocation().getRegionID());
		if (POH_ZONES.contains(event.getPlayer().getWorldLocation().getRegionID())) {
			log.info("Player entered a POH Zone");
			handleEventChange();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		log.info("Config changed");
		if(event.getGroup().equals("pohOptions")) {
			log.info("Config changed for {}", event.getGroup());
			handleEventChange();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if(event.getGameState() == GameState.LOADING)
		{
			regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
			if(POH_ZONES.contains(regionId)) {
				handleEventChange();
			}
		}
		if(event.getGameState() == GameState.LOGGED_IN)
		{
			regionId = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation()).getRegionID();
			if(POH_ZONES.contains(regionId))
			{
				log.info("onGameStateChanged recolor triggered");
				log.info("Current region ID: {}", regionId);
				handleEventChange();
			}
			if(!POH_ZONES.contains(regionId))
			{
				// clearing everything after leaving PoH
				resetSceneIDs();
				recordedGameObjects.clear();
				recordedGroundObjects.clear();
			}
		}
	}

	// Event block for subscribing to the events that trigger when objects are spawned
	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
		if (CURTAIN_IDS.contains(event.getDecorativeObject().getId())) {
			log.info("Found CurtainObjectId: {}", event.getDecorativeObject().getId());
			recordedCurtainObjects.add(event.getDecorativeObject());
		}
		else if (DECORATION_IDS.contains(event.getDecorativeObject().getId())) {
			log.info("Found DecorativeObject ID: {}", event.getDecorativeObject().getId());
			recordedDecorativeObjects.add(event.getDecorativeObject());
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event) {
		if (OBJECT_IDS.contains(event.getGameObject().getId())) {
			log.info("Found GameObject ID: {}", event.getGameObject().getId());
			recordedGameObjects.add(event.getGameObject());
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event) {
		if (GROUND_IDS.contains(event.getGroundObject().getId())) {
			log.info("Found GroundObject ID: {}", event.getGroundObject().getId());
			recordedGroundObjects.add(event.getGroundObject());
		}
	}

	// Event block for handling the config change, used to recolor objects based on plugin config
	public void handleEventChange() {
		log.info("handleEventChange triggered");
		if(config.carpetRecolor()) {
			log.info("Recolor carpet");
			carpetDataProcessor.recolorData(config.carpetColor(), config.carpetColor(), true);
			recolor("groundObject");
		} else {
			log.info("Clear carpet");
			clear("carpet");
		}
		if(config.bedRecolor()) {
			log.info("Recolor bed");
			bedDataProcessor.recolorData(config.bedColor(), config.bedColor(), true);
			recolor("gameObject");
		} else {
			log.info("Clear bed");
			clear("bed");
		}
		if(config.curtainRecolor()) {
			log.info("Recolor curtain");
			curtainDataProcessor.recolorData(config.curtainColor(), config.curtainColor(), true);
			recolor("curtainObject");
		} else {
			log.info("Clear curtain");
			clear("curtain");
		}
		if(config.otherWallObjectRecolor()) {
			log.info("Recolor other wall object");
			otherWallObjectDataProcessor.recolorData(config.otherWallObjectColor(), config.otherWallObjectColor(), true);
			recolor("otherWallObject");
		}
		else {
			log.info("Clear other wall object");
			clear("otherWallObject");
		}
	}

	// resets all objects to their default colors, if they are stored in the corresponding list.
	public void clear(String type)
	{
        switch (type) {
            case "all":
                for (GameObject g : recordedGameObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    bedDataProcessor.applyColors(g.getId(), "GameObject", model, false);
                }
                for (GroundObject g : recordedGroundObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    carpetDataProcessor.applyColors(g.getId(), "GroundObject", model, false);
                }
				for (DecorativeObject g : recordedCurtainObjects){
					Renderable renderable = g.getRenderable();
					Model model = verifyModel(renderable);
					curtainDataProcessor.applyColors(g.getId(), "DecorativeObject", model, false);
				}
                for (DecorativeObject g : recordedDecorativeObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    otherWallObjectDataProcessor.applyColors(g.getId(), "DecorativeObject", model, false);
                }
                break;
            case "carpet":
                for (GroundObject g : recordedGroundObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    carpetDataProcessor.applyColors(g.getId(), "GroundObject", model, false);
                }
                break;
            case "curtain":
                for (DecorativeObject g : recordedCurtainObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    curtainDataProcessor.applyColors(g.getId(), "DecorativeObject", model, false);
                }
                break;
			case "otherWallObject":
				for (DecorativeObject g : recordedDecorativeObjects) {
					Renderable renderable = g.getRenderable();
					Model model = verifyModel(renderable);
					otherWallObjectDataProcessor.applyColors(g.getId(), "DecorativeObject", model, false);
				}
				break;
            case "bed":
                for (GameObject g : recordedGameObjects) {
                    Renderable renderable = g.getRenderable();
                    Model model = verifyModel(renderable);
                    bedDataProcessor.applyColors(g.getId(), "GameObject", model, false);
                }
                break;
        }
	}

	// Recolors objects based on the object type
	public void recolor(String objectType)
	{
		log.info("Recolor triggered for {}", objectType);
        switch (objectType) {
            case "groundObject":
                for (GroundObject groundObject : recordedGroundObjects) {
					recolorObject(groundObject, carpetDataProcessor);
                }
                break;
            case "gameObject":
                for (GameObject gameObject : recordedGameObjects) {
					recolorObject(gameObject, bedDataProcessor);
                }
                break;
            case "curtainObject":
                for (DecorativeObject decorativeObject : recordedCurtainObjects) {
					recolorObject(decorativeObject, curtainDataProcessor);
                }
                break;
			case "otherWallObject":
				for (DecorativeObject decorativeObject : recordedDecorativeObjects) {
					recolorObject(decorativeObject, otherWallObjectDataProcessor);
				}
				break;
			case "all":
				for (DecorativeObject decorativeObject : recordedCurtainObjects) {
					recolorObject(decorativeObject, curtainDataProcessor);
				}
				for (DecorativeObject decorativeObject : recordedDecorativeObjects) {
					recolorObject(decorativeObject, otherWallObjectDataProcessor);
				}
				for (GameObject gameObject : recordedGameObjects) {
					recolorObject(gameObject, bedDataProcessor);
				}
				for (GroundObject groundObject : recordedGroundObjects) {
					recolorObject(groundObject, carpetDataProcessor);
				}
		}
	}

	// Recolors decorative objects
	public void recolorObject(DecorativeObject decorativeObject, ModelDataProcessor processor)
	{
		Renderable renderable = decorativeObject.getRenderable();
		Model model = verifyModel(renderable);
		if (model == null)
		{
			log.info("recolorObject returned null!");
			return;
		}
		processor.applyColors(decorativeObject.getId(), "DecorativeObject", model, true);
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	// Recolors game objects
	public void recolorObject(GameObject gameObject, ModelDataProcessor processor)
	{
		Renderable renderable = gameObject.getRenderable();
		Model model = verifyModel(renderable);
		if (model == null)
		{
			log.info("recolorObject returned null!");
			return;
		}
		processor.applyColors(gameObject.getId(), "GameObject", model, true);
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	// Recolors ground objects
	public void recolorObject(GroundObject groundObject, ModelDataProcessor processor)
	{
		Renderable renderable = groundObject.getRenderable();
		Model model = verifyModel(renderable);
		if (model == null)
		{
			log.info("recolorObject returned null!");
			return;
		}
		processor.applyColors(groundObject.getId(), "GroundObject", model, true);
		recordedModels.add(model);
		sceneIDs.add(model.getSceneId());
		model.setSceneId(0);
	}

	// Verifies and returns a renderable Model, or null if not available
	private Model verifyModel(Renderable renderable)
	{
		if (renderable instanceof Model)
		{
			return (Model) renderable;
		}
		
		Model model = renderable.getModel();
		if (model == null)
		{
			log.info("verifyModel returned null!");
		}
		return model;
	}

	// Restores original scene IDs for recorded models and clears the lists
	private void resetSceneIDs()
	{
		for (int i = 0; i < sceneIDs.size(); i++)
		{
			recordedModels.get(i).setSceneId(sceneIDs.get(i));
		}
		recordedModels.clear();
		sceneIDs.clear();
	}
}
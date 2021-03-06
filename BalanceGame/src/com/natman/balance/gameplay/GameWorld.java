package com.natman.balance.gameplay;

import java.util.Iterator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.joints.RevoluteJointDef;
import com.natman.balance.gameplay.entities.Boulder;
import com.natman.balance.gameplay.entities.Pillar;
import com.natman.balance.gameplay.entities.Platform;
import com.natman.balance.gameplay.entities.Player;
import com.natman.balance.gameplay.entities.PowerUp;
import com.natman.balance.utils.Convert;
import com.natman.balance.utils.Random;
import com.natman.balance.utils.SoundManager;
import com.natman.balance.utils.SpriteSheet;

public class GameWorld implements InputProcessor {
	
	//region Fields
	
	private PhysicsWorld world;
	
	private OrthographicCamera camera;
	private Box2DDebugRenderer worldRenderer;
	private Matrix4 worldMatrix;
	
	private BitmapFont font;
	private Random r = new Random();
	
	private SpriteSheet spriteSheet;
	
	private Body floor;
	private Player player;
	
	private boolean debugRender = false;
	
	public float furthestX = 0;
	
	private float lastX = 0;
	private float lastWidth = maxPlatformWidth;
	private float lastHeight = firstPillarHeight;
	
	private float boulderChance = 0.005f;
	
	public float highScore = 0;
	
	public int jumps = 0;
	public int bonks = 0;
	
	public boolean gameOver = false;
	
	private boolean movingLeft; 
	private boolean movingRight;
	
	//endregion
	
	//region Config
	
	public static final float floorX = 0;
	public static final float floorY = Convert.pixelsToMeters(-550);
	public static final float floorWidth = 8000;
	public static final float floorHeight = Convert.pixelsToMeters(5);
	
	private static final float firstPillarHeight = 27f;
	private static final float minPillarHeight = 23f;
	private static final float maxPillarHeight = 34f;
	private static final float upMax = 4f;
	
	private static final float minPlatformWidth = 10f;
	private static final float maxPlatformWidth = 20f;
	
	private static final float maxDistance = 6f;
	private static final float creationDistance = maxPlatformWidth * 2;
	
	private static final float removalDistance = Convert.pixelsToMeters(500);
	
	private static final float boulderSpawnRadius = Convert.pixelsToMeters(380);
	
	private static final float powerupDistance = Convert.pixelsToMeters(400);
	private static final float powerupChance = 0.001f;
	private static final float powerupY = Convert.pixelsToMeters(180);
	
	private static final float playerDeathZone = Convert.pixelsToMeters(-280);
	
	//endregion
	
	public OrthographicCamera getCamera() {
		return camera;
	}
	
	//region Constructor
	
	public GameWorld() {
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();
		
		camera = new OrthographicCamera();
		camera.setToOrtho(false, w, h);
		
		world = new PhysicsWorld(new Vector2(0, -10));
		world.getWorld().setContactListener(new ContactManager(this));
		worldRenderer = new Box2DDebugRenderer();
		
		font = new BitmapFont();
		font.setColor(Color.WHITE);
		
		spriteSheet = new SpriteSheet(new Texture(Gdx.files.internal("data/sprites.png")));
		
		initializeSpriteSheet();
		initializeWorld();
		
		Preferences prefs = Gdx.app.getPreferences("CrashingDownData");
		highScore = prefs.getFloat("HighScore", 0f);
		
		SoundManager.playSong("Music", 0.6f, true);
	}
	
	//endregion
	
	//region Entities
	
	private void initializeSpriteSheet() {
		spriteSheet.addRegion("Player", new Rectangle(0, 0, 16, 40));
		spriteSheet.addRegion("Platform", new Rectangle(16, 0, 16, 40));
		spriteSheet.addRegion("Rock", new Rectangle(32, 0, 23, 24));
		spriteSheet.addRegion("SpeedPowerup", new Rectangle(55, 0, 19, 19));
		spriteSheet.addRegion("JumpPowerup", new Rectangle(74, 0, 19, 19));
	}

	private void initializeWorld() {
		createFloor();

		player = new Player(spriteSheet, world.getWorld());
		
		createStartingTower();
		
		for (int i = 17; i < 30; i += lastWidth) {
			createTower(i);
		}
	}
	
	private void createFloor() {
		BodyDef bd = new BodyDef();
		bd.type = BodyType.StaticBody;
		bd.position.set(floorX, floorY);
		
		PolygonShape shape = new PolygonShape();
		shape.setAsBox(floorWidth / 2, floorHeight / 2);
		
		FixtureDef fd = new FixtureDef();
		fd.shape = shape;
		
		Body body = world.getWorld().createBody(bd);
		body.createFixture(fd);
		
		floor = body;
		
		shape.dispose();
	}
	
	private void createStartingTower() {
		float height = firstPillarHeight;
		
		Entity p1 = createPillar(0, height);
		Entity p2 = createPlatform(0, height, maxPlatformWidth);
		
		createAnchor(height, p1, p2);
	}
	
	private void createTower(float x) {
		float height = r.nextFloat(minPillarHeight, Math.min(maxPillarHeight, lastHeight + upMax));
		
		Entity p1 = createPillar(x, height);
		
		Entity p2 = createPlatform(x, height, r.nextFloat(minPlatformWidth, maxPlatformWidth));
		
		createAnchor(height, p1, p2);
		
		lastHeight = height;
		lastX = x;
	}

	private void createAnchor(float height, Entity p1, Entity p2) {
		Body b1 = p1.body;
		Body b2 = p2.body;
		
		RevoluteJointDef jointDef = new RevoluteJointDef();
		jointDef.bodyA = b2;
		jointDef.bodyB = b1;
		
		jointDef.localAnchorA.set(0, 0);
		jointDef.localAnchorB.set(0, height / 2);
		
		jointDef.enableLimit = true;
		jointDef.lowerAngle = (float) Math.toRadians(-60);
		jointDef.upperAngle = (float) Math.toRadians(60);
		
		world.getWorld().createJoint(jointDef);
	}
	
	private Entity createPlatform(float x, float y, float width) {
		Entity e = new Platform(spriteSheet, world.getWorld(), x, y, width);
		lastWidth = width;
		return e;
	}

	private Entity createPillar(float x, float height) {
		Entity e = new Pillar(spriteSheet, world.getWorld(), x, height);
		return e;
	}
	
	private void createBoulder(float x) {
		new Boulder(spriteSheet, world.getWorld(), x);
	}
	
	//endregion
	
	//region Game Loop
	
	public void render(float delta, SpriteBatch batch) {
		camera.position.set(Convert.metersToPixels(new Vector3(player.body.getPosition().x, 0, 0)));
		
		camera.update();
		
		removeOffscreenEntities();
		
		if (player.body.getPosition().x > furthestX) {
			furthestX = player.body.getPosition().x;
			
			boulderChance += 0.00001f;
			
			if (furthestX >= lastX - creationDistance) {
				createTower(lastX + r.nextFloat(lastWidth, lastWidth + maxDistance));
			}
			
			for (int i = 0; i < r.floatToInt(powerupChance); i++) {
				new PowerUp(spriteSheet, world.getWorld(), furthestX + powerupDistance, powerupY);
			}
		}
		
		for (int i = 0; i < r.floatToInt(boulderChance); i++) {
			float x = r.nextFloat(player.body.getPosition().x - boulderSpawnRadius, player.body.getPosition().x + boulderSpawnRadius);
			createBoulder(x);
		}
		
		batch.setProjectionMatrix(camera.combined);
		
		batch.begin();
		

		if (debugRender) font.draw(batch, "" + Gdx.graphics.getFramesPerSecond(), camera.position.x, camera.position.y); //Performance info
		
		if (furthestX > highScore) highScore = furthestX;
		
		float x = camera.position.x - camera.viewportWidth / 2;
		float y = camera.position.y + camera.viewportHeight / 2;
		font.draw(batch, "m traveled: " + (int) furthestX + "   furthest traveled: " + (int) highScore, x, y);
		
		Iterator<Body> it = world.getWorld().getBodies();
		while (it.hasNext()) {
			Body body = it.next();
			
			Entity e = (Entity) body.getUserData();
			
			if (e != null) e.draw(batch);
		}
		
		batch.end();
		
		player.processPowerups(delta);
		
		if (Gdx.input.isKeyPressed(Keys.F1)) {
			debugRender = !debugRender;
		}
		
		if (debugRender) {
			worldMatrix = new Matrix4(camera.combined);
			worldMatrix.scale(Convert.getPixelMeterRatio(), Convert.getPixelMeterRatio(), Convert.getPixelMeterRatio());
			
			worldRenderer.render(world.getWorld(), worldMatrix);
		}
		
		floor.getPosition().set(new Vector2(player.body.getPosition().x, floorY));
		
		if (movingLeft) {
			player.moveLeft();
		}
		
		if (movingRight) {
			player.moveRight();
		}
		
		world.process(delta);
	}

	private void removeOffscreenEntities() {		
		Vector2 cameraPos = Convert.pixelsToMeters(new Vector2(camera.position.x, camera.position.y));
		
		Iterator<Body> it = world.getWorld().getBodies();
		while (it.hasNext()) {
			Body body = it.next();
			
			if (body == null || body.getUserData() == null) continue;
			
			if (body.getUserData() instanceof Pillar || body.getUserData() instanceof Platform) {
				if (body.getPosition().x < Convert.pixelsToMeters(camera.position.x - camera.viewportWidth / 2) - maxPlatformWidth / 2) {
					Entity e = (Entity) body.getUserData();
					
					delete(e);
				}
				
				continue;
			}
			
			if (body.getUserData() instanceof Player) {
				if (body.getPosition().y < playerDeathZone) {
					Entity e = (Entity) body.getUserData();
					
					delete(e);
					
					gameOver = true;
				}
				
				continue;
			}
			
			if (body.getPosition().dst(cameraPos) > removalDistance) {
				Entity e = (Entity) body.getUserData();
				
				delete(e);
			}
			
		}
	}

	public void resize(int width, int height) {
		camera.setToOrtho(false, width, height);
		camera.position.set(0, 0, 0);
	}
	
	public void dispose() {
		world.dispose();
		
		Preferences prefs = Gdx.app.getPreferences("CrashingDownData");
		
		prefs.putFloat("HighScore", highScore);
		
		prefs.flush();
	}
	
	public void delete(Entity e) {
		Body body = e.body;
		world.getWorld().destroyBody(body);
		
		e = null;
	}

	@Override
	public boolean keyDown(int keycode) {
		if (keycode == Keys.LEFT) {
			movingLeft = true;
		} else if (keycode == Keys.RIGHT) {
			movingRight = true;
		}
		
		if (keycode == Keys.SPACE && player.canJump) {
			player.jump();
			jumps++;
			SoundManager.playSound("Jump");
		}
		
		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		if (keycode == Keys.LEFT) {
			movingLeft = false;
		} else if (keycode == Keys.RIGHT) {
			movingRight = false;
		}
		
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean scrolled(int amount) {
		// TODO Auto-generated method stub
		return false;
	}
	
	//endregion
	
}
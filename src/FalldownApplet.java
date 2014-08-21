import hsrw.illumination.client.intern.*;
import hsrw.illumination.client.intern.arbiter.*;

import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import processing.core.PApplet;
import processing.core.PImage;
/**
 * Created by Peter Mösenthin.
 */
@SuppressWarnings("serial")
public class FalldownApplet extends PApplet {

    // #########################################################################
    // SETTINGS - Use this to modify the game
    // #########################################################################

    /* Setup for the server */
    PImage serverImage;
    //APIClientIntern(String ip, int port, String uname, String upass)
    APIClientIntern client;

    /**
     * Saves connected player
     */
    private Player player;
    private boolean playerIsGaming = false;

    /*
    Control constants used by the server
     */
    private static final String CTL_LEFT = "1";
    private static final String CTL_RIGHT = "3";
    private static final String CTL_SPECIAL = "0";


    /**
     If this is true a connection with the server will be established and a
     picture will be sent every frame to the server
     This is used to prevent exceptions for testing if server is unreachable.
     */
    public boolean serverAPI = true;

    private static final String SERVER_IP = "192.168.0.120";
    //private static final String SERVER_IP = "testserver.kamp-lintfort-leuchtet.de";
    private static final int SERVER_PORT = 3001;
    private static final String SERVER_LOGIN = "PeterVonOz";
    private static final String SERVER_PASSWORD = "12345678";
    private static final String SCHEDULE_NAME = "Falldown";


    /* Bounds of grid/display */
    public int GRID_X = 9;
    public int GRID_Y = 14;

    /**
    Columns to display.
    The grid will be divided after every column
    */
    public static final int COLUMNS = 3;

    /** Scaling factor for the processing window */
    public static final float SCALE_FACTOR = 3.0f;

    /** FPS is used for the overall speed (processing setting) */
    public static final int FPS = 30;

    /*
    These values are used to display the debugging grid
    and won't affect the actual game
    */
    /**
     * will move the debuggrid out of the corner
     */
    public static final int GRID_OFFSET = 15;
    /**
     * is used to display the space between columns
     */
    public static final int COLUMN_GAP = 5;

    /*
    Logger and log level
    */
    private static final Level LOG_LEVEL = Level.INFO;
    public static Logger logger = Logger.getAnonymousLogger();


    /**
     * The framecount is passed to the falldown game to determine updates
     */
    private int frameCount = 0;

    /** Currently used for some random colors in the GameEnd animation */
    private Random random = new Random();


    /** Game instance */
    Falldown falldown;

    // #########################################################################
    // PROCESSING SPECIFIC METHODS
    // #########################################################################

    /**
     * Used by processing to initialize the applet
     */
    public void setup() {
        // Set Log-level
        System.out.println("LOGGING LEVEL: " + LOG_LEVEL);
        logger.setLevel(LOG_LEVEL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(LOG_LEVEL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);

        // Log into the server
        if (serverAPI) {
            connectApiClient();
        }


        // window size
        size(GRID_X * (int) (14 * SCALE_FACTOR), GRID_Y
                * (int) (11 * SCALE_FACTOR));

        logger.log(Level.INFO, "Grid set to " + GRID_X + "x" + GRID_Y);

        // Set up the falldown game
        falldown = new Falldown(GRID_X, GRID_Y, COLUMNS);

        // This image will be sent to the server
        serverImage = createImage(GRID_X, GRID_Y, RGB);

        // Disable AA to prevent weird fading in the preview window
        noSmooth();

        // Set framerate for processing engine
        frameRate(FPS);
    }


    public void connectApiClient(){
        client = new APIClientIntern(SERVER_IP,
                SERVER_PORT,
                SERVER_LOGIN,
                SERVER_PASSWORD);
        client.debug = true;
        GRID_X = client.getViewportWidth();
        GRID_Y = client.getViewportHeight();
        client.scheduleAddGame(SCHEDULE_NAME);
        client.createCanvas(GRID_X, GRID_Y);
    }

    /**
     * Called every frame to update and display everything
     */
    public void draw() {
        logger.log(Level.FINEST, "Frame " + frameCount);

        //Handle client API if activated
        if(serverAPI) {
            //Accept newest player while no one is playing
	        if(client.playerWantsToEnterGame() && !playerIsGaming) {
	        	logger.log(Level.INFO, "Player wants to enter game");
                if(player != null){
                    logger.log(Level.INFO,"Kicking player with ID: " + player.getId());
                    player.kick();
                }
	        	player = client.getEnteringPlayer();
                logger.log(Level.INFO,"Player entered with ID: " + player.getId());
	        	player.accept();
	        	logger.log(Level.INFO, "Player(" + player.getId() + ") accepted");
            }

            if(client.isLive() && !playerIsGaming){
                logger.log(Level.INFO, "Client is live");
                if(player != null){
                logger.log(Level.INFO, "Starting liveplay");
                playerIsGaming = true;
                falldown.resetAndPurge();
                }
            }

            // Go to idle state for next play
            if(falldown.isPlayerDead()){
                if(player != null){
                    player.kick();
                    player = null;
                }
                playerIsGaming = false;
                falldown.playIdleAnimation();
                client.scheduleAddGame(SCHEDULE_NAME);
                //connectApiClient();
            }

            if(client.isLive()){
                manageServerControls();
            }
        }


        //Static output like the debug grid
        background(150);
        translate(GRID_OFFSET, GRID_OFFSET);
        scale(SCALE_FACTOR);
        drawGridLines();


        // Reset the image
        serverImage = createImage(GRID_X, GRID_Y, RGB);

        // Update game behavior every frame
        falldown.update(frameCount);

        // Draw level to the debug grid
        drawToGrid(falldown.getLevelGrid());

        // Fill the image for the server
        drawOutputImage(falldown.getLevelGrid());

        // Debug output
        image(serverImage, GRID_X * 12, GRID_Y);

        // Send the created image to the server
        if(serverAPI){
            logger.log(Level.FINEST, "Sending game image to the Server");
            client.drawCanvas(serverImage);
        }





        // Count frames to determine updates in falldown game
        frameCount++;
    }

    /**
     * <p>Handles key events. So far there are three buttons used within the game</p>
     *
     * <p>a: Move the player one pixel to the left</p></br>
     * <p>d: Move the player one pixel to the right</p></br>
     * <p>r: Resets the level. Is used for debugging or to trigger a reset</p>
     */
    public void keyPressed() {
        if (key == 'a') {
            falldown.movePlayerLeft();
            logger.log(Level.FINE, "Left pressed");
        }
        if (key == 'd') {
            falldown.movePlayerRight();
            logger.log(Level.FINE, "Right pressed");
        }
        if (key == 'r') {
            falldown.resetAndPurge();
            logger.log(Level.FINE, "Space pressed");
        }
    }

    public void manageServerControls(){
        if(client.hasMessageReceived()) {
            APIMessage m = client.getReceivedMessage();
            System.out.println("Server: " + m.getMessage());
            if(m.getMessage().equals(CTL_LEFT)) {
                falldown.movePlayerLeft();
                logger.log(Level.INFO, "Client received message: LEFT");
            } else if(m.getMessage().equals(CTL_RIGHT)) {
                falldown.movePlayerRight();
                logger.log(Level.INFO, "Client received message: RIGHT");
            }else if(m.getMessage().equals(CTL_SPECIAL)) {
                falldown.resetAndPurge();
                logger.log(Level.INFO, "Client received message: SPECIAL");
            }
        }

    }

    // #########################################################################
    // METHODS
    // #########################################################################

    /**
     * <p>Draws lines to display a grid in which the level can be displayed</p>
     * <p>A single "pixel" is 10px wide Scaling is done through the engine</p>
     */
    private void drawGridLines() {
        fill(150);
        stroke(0);
        int gap = 0;
        for (int i = 0; i < GRID_X; i++) {
            if (i % (GRID_X / COLUMNS) == 0) {
                gap += COLUMN_GAP;
            }
            for (int j = 0; j < GRID_Y; j++) {
                rect(i * 10 + gap, j * 10, 10, 10);
            }
        }
    }

    /**
     * Draws an array to the screen / debug grid
     *
     * @param frame The Array to display
     */
    private void drawToGrid(int[][] frame) {
        int gap = 0;
        for (int i = 0; i < frame.length; i++) {
            if (i % (GRID_X / COLUMNS) == 0) {
                gap += COLUMN_GAP;
            }
            for (int j = 0; j < frame[0].length; j++) {
                if (frame[i][j] == Falldown.NORMAL_BLOCK) {
                    stroke(0, 255, 0);
                    fill(0, 255, 0);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                } else if (frame[i][j] == Falldown.FADING_BLOCK) {
                    stroke(0, 255, 0, 100);
                    fill(0, 255, 0, 100);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                } else if (frame[i][j] == Falldown.PLAYER_BLOCK) {
                    stroke(0, 0, 200);
                    fill(0, 0, 200);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                } else if (frame[i][j] == Falldown.TREASURE_BLOCK) {
                    stroke(255, 255, 0);
                    fill(255, 255, 0);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                } else if (frame[i][j] == Falldown.RED_BLOCK) {
                    stroke(255, 0, 0);
                    fill(255, 0, 0);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                } else if(frame[i][j] == Falldown.RANDOM_BLOCK){
                    int r = random.nextInt(255);
                    int g = random.nextInt(255);
                    int b = random.nextInt(255);
                    stroke(r, g, b);
                    fill(r, g, b);
                    rect(i * 10 + 1 + gap, j * 10 + 1, 8, 8);
                }
            }
        }

    }

    /**
     * <p>Draws the image that will be sent to server Image</p>
     * <p>Size is set by <code>GRID_X</code> and <code>GRID_Y</code></p>
     *
     * @param grid The grid that will be drawn to the image
     */
    private void drawOutputImage(int[][] grid) {
        int count = 0;
        for (int i = 0; i < grid[0].length; i++) {
            for (int j = 0; j < grid.length; j++) {
                if (grid[j][i] == Falldown.EMPTY_BLOCK) {
                    serverImage.pixels[count] = color(0, 0, 0);
                } else if (grid[j][i] == Falldown.NORMAL_BLOCK) {
                    serverImage.pixels[count] = color(0, 255, 0);
                } else if (grid[j][i] == Falldown.FADING_BLOCK) {
                    serverImage.pixels[count] = color(0, 150, 0);
                } else if (grid[j][i] == Falldown.PLAYER_BLOCK) {
                    serverImage.pixels[count] = color(0, 0, 255);
                } else if (grid[j][i] == Falldown.TREASURE_BLOCK) {
                    serverImage.pixels[count] = color(255, 255, 0);
                } else if (grid[j][i] == Falldown.RED_BLOCK) {
                    serverImage.pixels[count] = color(255, 0, 0);
                }else if(grid[j][i] == Falldown.RANDOM_BLOCK){
                    int r = random.nextInt(255);
                    int g = random.nextInt(255);
                    int b = random.nextInt(255);
                    serverImage.pixels[count] = color(r, g, b);
                }
                count++;
            }
        }
    }

    // #########################################################################
    // ACCESSORS
    // #########################################################################

    /**
     * Access the logger of this class
     * @return logger
     */
    public static Logger getLogger() {
        return logger;
    }

}

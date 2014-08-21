import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * Created by Peter Mösenthin.
 */
public class Falldown {

    // #########################################################################
    // Level parameters and indicators
    // #########################################################################

    /* Current and next levelgrid */
    private int[][] levelGrid;
    private int[][] nextGrid;

    /* Level bounds */
    private int columns;
    private int columnPixelWidth;
    private int gridWidth;
    private int gridHeight;

    /* Treasure position */
    private int treasureX;
    private int treasureY;

    /* Player position */
    private int playerX;
    private int playerY;

    private Random random = new Random();

    /* These Constants are used to identify blocks in the grid */
    public static final int EMPTY_BLOCK = 0;
    public static final int NORMAL_BLOCK = 1;
    public static final int FADING_BLOCK = 2;
    public static final int PLAYER_BLOCK = 3;
    public static final int TREASURE_BLOCK = 4;
    public static final int RED_BLOCK = 5;
    public static final int RANDOM_BLOCK = 6;

    /*Level specific settings */
    private float[] level_refreshLevel = {1f, 1.2f, 1.5f};
    private int[] level_maxTreasureSteps = {4, 3, 2};
    private static final int levelCount = 3;
    private int currentLevel;

    /* Animation */
    private static final long ANIMATION_DELAY = 20; // milliseconds
    private boolean animationActive = false;
    private ArrayList<Thread> animationRunning = new ArrayList<Thread>();

    /**
     * The refresh level sets the speed the game Grid is being moved
     */
    public float refreshLevel = 1f;

    /**
     * Indicates if the treasure has been reached.
     * Triggers the end of the level
     */
    private boolean treasureReached = false;

    /**
     * <p>Indicates if the treasure got out of the array bounds.</br>
     * This doesn't mean an ArrayOutOFBoundsException.</p>
     * <p>Triggers treasure <b>replacement</b>.</p>
     */
    private boolean treasureOutOfBounds = false;

    /**
     * Stores treasure move steps
     */
    int treasureSteps = 0;

    /**
     * Stores the level specific bound for treasure steps
     */
    private int maxTreasureSteps;

    /**
     * Indicates if the player is dead
     * Triggers the end of the game
     */
    private boolean playerDead = false;

    /**
     * Indicates if the game is Active
     * Can be triggered by the last level or if the game ends
     * Could start a transition to the next game
     */
    private boolean gameActive = false;

    /**
     * Use logger from game applet
     */
    private static Logger logger = FalldownApplet.getLogger();

    /**
     * Set if holes should be generated through which the player can fall
     */
    private boolean generateHoles = false;
    /* Chance of regenerating a hole in percent */
    private static final int HOLE_CHANCE = 20;


    // #########################################################################
    // CONSTRUCTORS & INITIALIZATION
    // #########################################################################

    /**
     * Sets up a new Falldown game. These settings cannot be changed once the
     * game is initialized.
     *
     * @param gridWidth  Width of the array used for the game
     * @param gridHeight Height of the array used for the game
     * @param columns    Number of columns in the game
     */
    public Falldown(int gridWidth, int gridHeight, int columns) {
        levelGrid = new int[gridWidth][gridHeight];
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.columns = columns;
        this.columnPixelWidth = gridWidth / columns;
        logger.log(Level.INFO, "Falldown game created");
        this.playPlaceholderAnimation();
    }

    /**
     * Resets the game to its initial state. This is equivalent of creating a
     * new instance with the same parameters
     */
    public void resetAndPurge() {
        logger.log(Level.INFO, "The game will be reset");
        purge();
        initLevelGrid();
        currentLevel = 0;
        //Start with base level (0)
        setLevelParams(0);
    }


    /**
     * Purges game parameters for a restart or start of the next Level
     */
    private void purge() {
        logger.log(Level.FINE, "Purging game");
        endAllAnimations();
        treasureSteps = 0;
        playerDead = false;
        treasureReached = false;
        gameActive = true;
        generateHoles = false;
    }


    /**
     * Initializes the levelGrid with horizontal lines. Can also generate two
     * pixel wide holes (Set through the <code>GENERATE_HOLES</code> constant
     */
    private void initLevelGrid() {
        logger.log(Level.INFO, "Initializing level grid");
        int holePosition;
        levelGrid = new int[gridWidth][gridHeight];
        for (int i = 0; i < levelGrid[0].length; i++) {
            // Generate random position for holes
            holePosition = (int) (Math.random() * levelGrid.length - 1);
            if (i % 3 == 0) {
                for (int j = 0; j < levelGrid.length; j++) {
                    if (j == holePosition) {
                        if (generateHoles) {
                            levelGrid[j][i] = EMPTY_BLOCK;
                        } else {
                            levelGrid[j][i] = NORMAL_BLOCK;
                        }
                    } else {
                        levelGrid[j][i] = NORMAL_BLOCK;
                    }
                }
            }
        }
        // Currently the player has a fixed starting position
        setPlayerPosition(4, 2);
        levelGrid[4][2] = PLAYER_BLOCK;
        // Treasure is placed randomly
        setTreasurePositionRandom();
    }

    /**
     * <p>Initiates a new level.</p>
     * <p>Sets all necessary level settings and starts the level</p>
     *
     * @param level Number of level to load
     */
    private void setLevelParams(int level) {
        logger.log(Level.FINE, "Setting up level " + level);
        maxTreasureSteps = level_maxTreasureSteps[level];
        refreshLevel = level_refreshLevel[level];
        if (currentLevel == 2) {
            generateHoles = true;
        }
    }

    /**
     * Loads next level or triggers game end if every level is played
     */
    private void advanceLevel() {
        logger.log(Level.INFO, "Advancing to next level");
        currentLevel++;

        if (currentLevel >= levelCount) {
            playGameEndAnimation();
        } else {
            purge();
            setLevelParams(currentLevel);
            initLevelGrid();
        }

    }


    // #########################################################################
    // UPDATE METHODS
    // #########################################################################

    /**
     * This method is called every frame by the executing class
     *
     * @param frame Pass the frame number to determine if update is needed
     */
    public void update(int frame) {
        // update everything if the game is active
        if (gameActive) {

            // Update the game if player is alive
            if (!playerDead) {

                if (!treasureReached) {
                    // Normal game update
                    if (frame % (int) (FalldownApplet.FPS / refreshLevel) == 0) {
                        updateGrid();
                    }
                    updateNonStatic();
                }
            }
        }

    }

    /**
     * Call this method every time the grid should be moved by one pixel/step.
     * Calling this every frame would result in very fast movement.
     * <p/>
     * Moves every even column up and every uneven column down. Starting at 0
     * this means the first one moves up, the second moves down and so on.
     * <p/>
     * If a treasure or player is upon a <code>NORMAL_BLOCK</code> it will be moved too.
     * Note: Normal player movement and "falling" player movement should be
     * updated every frame and is therefore handled by <code>updateNonStatic()</code> method.
     * <p/>
     * If a row of blocks has been moved out of bounds a new row will be
     * generated in the next call. Blocks that are about to be pushed out of
     * bounds are set to <code>FADING_BLOCK</code> to indicate disappearance in the next grid
     * update.
     * <p/>
     * If the player is moved out of bounds it will result in a player death. If
     * the treasure is moved out of bounds it will be placed on a different
     * position.
     */
    private void updateGrid() {
        logger.log(Level.FINE, "Updating levelgrid");
        nextGrid = new int[gridWidth][gridHeight];
        treasureSteps++;

        // Process every column individually depending on the position
        for (int column = 0; column < columns; column++) {

            // Calculate upward movement for even columns
            if (column % 2 == 0) {
                updateColumnUpward(column);
                // Calculate downward movement for even columns
            } else {
                updateColumnDownward(column);
            }
        }
        // Set new calculated grid
        levelGrid = nextGrid;
    }

    /**
     * Splitted Method for upward column processing. See documentation of
     * <code>updateGrid()</code> for more detailed information
     *
     * @param column Column to be processed with this behaviour.
     */
    private void updateColumnUpward(int column) {
        int colX;
        for (int posY = 0; posY < gridHeight; posY++) {
            for (int posX = 0; posX < columnPixelWidth; posX++) {

                // Pre calculate x-position
                colX = posX + column * columnPixelWidth;

                // Do whatever is necessary to the specific block
                logger.log(Level.FINEST, "Processing upward " + colX + "/"
                        + posY);

                // Second position
                if (posY == 1) {
                    // Fade out the block because it will be gone in the next
                    // step
                    if (levelGrid[colX][posY] == NORMAL_BLOCK) {
                        nextGrid[colX][posY - 1] = FADING_BLOCK;
                    }

                    // Position in bounds
                } else if (posY > 1) {
                    // Move up
                    if (levelGrid[colX][posY] == NORMAL_BLOCK) {
                        nextGrid[colX][posY - 1] = levelGrid[colX][posY];
                    }
                }

                // Bottom position
                if (posY == gridHeight - 1) {
                    // Create new block
                    if (isRowNonStatic(column, gridHeight - 1)&&
                            isRowNonStatic(column, gridHeight - 2)) {
                        if(generateHoles && (random.nextInt(100) < HOLE_CHANCE)){
                            nextGrid[colX][gridHeight - 1] = EMPTY_BLOCK;
                        } else {
                            nextGrid[colX][gridHeight - 1] = NORMAL_BLOCK;
                        }

                        logger.log(Level.FINEST, "Created new block at " + colX
                                + "/" + (gridHeight - 1));
                    }
                }

                // Move the player up and trigger death if necessary
                if (levelGrid[colX][posY] == PLAYER_BLOCK) {
                    // Player is at the top position and dies moving up
                    if (playerY == 0) {
                        logger.log(Level.FINE, "Player died moving up");
                        this.playerDead = true;
                        playPlayerDeadAnimation();
                    } else if (playerY > 0) {
                        logger.log(Level.FINE, "Moving player up");
                        setPlayerPosition(playerX, playerY - 1);
                    }
                }

                // Moves the treasure up or replaces it if necessary
                if (levelGrid[colX][posY] == TREASURE_BLOCK) {
                    // Treasure is at the top and will be pushed out of
                    // bounds. Trigger new positon
                    if (treasureY == 0) {
                        logger.log(Level.INFO, "Treasure out of bounds (top)");
                        treasureOutOfBounds = true;
                        // Normal case: move treasure up
                    } else {
                        logger.log(Level.FINEST, "Moving treasure up");
                        treasureY = treasureY - 1;
                    }
                }
            }
        }

    }

    /**
     * Splitted Method for downward column processing. See documentation of
     * <code>updateGrid()</code> for more detailed information
     *
     * @param column Column to be processed with this behaviour.
     */
    private void updateColumnDownward(int column) {
        int colX;
        for (int posY = 0; posY < gridHeight; posY++) {
            for (int posX = 0; posX < columnPixelWidth; posX++) {

                // Pre calculate x-position for specified column
                colX = posX + column * columnPixelWidth;

                // Do whatever is necessary to the specific block
                logger.log(Level.FINEST, "Processing downward " + colX + "/"
                        + posY);

                // second last position
                if (posY == gridHeight - 2) {
                    // Fade out the block because it will be gone in the next
                    // step
                    if (levelGrid[colX][posY] == NORMAL_BLOCK) {
                        nextGrid[colX][posY + 1] = FADING_BLOCK;
                    }

                    // Simple move (position in bounds)
                } else if (posY < gridHeight - 2) {
                    if (levelGrid[colX][posY] == NORMAL_BLOCK) {
                        nextGrid[colX][posY + 1] = levelGrid[colX][posY];
                    }
                }

                if (posY == 0) {
                    if (isRowNonStatic(column, 0)
                            && isRowNonStatic(column, 1)) {
                        if(generateHoles && (random.nextInt(100) < HOLE_CHANCE)){
                            nextGrid[colX][0] = EMPTY_BLOCK;
                        } else {
                            nextGrid[colX][0] = NORMAL_BLOCK;
                        }
                        logger.log(Level.FINEST, "Created new block at " + colX
                                + "/0");
                    }
                    }


                // Moves the treasure down or replaces it if necessary
                if (levelGrid[colX][posY] == TREASURE_BLOCK) {
                    // Treasure is at the bottom and will be pushed out of
                    // bounds. Trigger new positon
                    if (treasureY == gridHeight - 1) {
                        logger.log(Level.FINE,
                                "Treasure out of bounds (bottom)");
                        treasureOutOfBounds = true;
                        // Normal case: move treasure down
                    } else {
                        logger.log(Level.FINEST, "Moving treasure down");
                        setTreasurePosition(treasureX, treasureY + 1);
                    }
                }

            }
        }
    }

    private boolean isRowNonStatic(int column, int height){
        boolean rowNonStatic = true;
        for(int i =0; i< columnPixelWidth;i++){
            if(isStaticBlock(levelGrid[i + column * columnPixelWidth][height])){
                rowNonStatic = false;
            }
        }
        return rowNonStatic;
    }

    /**
     * <p>Removes every non static block from the grid.</p>
     * <p>Non static blocks are the player, treasure etc.</p>
     */
    private void clearNonStatic() {
        for (int i = 0; i < gridWidth; i++) {
            for (int j = 0; j < gridHeight; j++) {
                if (levelGrid[i][j] == PLAYER_BLOCK) {
                    levelGrid[i][j] = EMPTY_BLOCK;
                } else if (levelGrid[i][j] == TREASURE_BLOCK) {
                    levelGrid[i][j] = EMPTY_BLOCK;
                }
            }
        }
    }

    /**
     * Updates the non-static elements within the level. Should be called every
     * frame to ensure correct behavior like falling down, dying etc.
     */
    private void updateNonStatic() {
        clearNonStatic();
        updateTreasure();
        updatePlayer();
    }

    /**
     * <p>Updates the treasure.</p>
     * <p>Replaces the treasure after it reaches <code>maxTreasureSteps</code></p>
     */
    private void updateTreasure() {
        // If treasure has been pushed out of bounds place it somewhere else
        if (treasureOutOfBounds) {
            setTreasurePositionRandom();
        }
        // Make the treasure fall
        if (treasureY < gridHeight - 1) {
            if (isNonStaticBlock(levelGrid[treasureX][treasureY + 1])) {
                setTreasurePosition(treasureX, treasureY + 1);
            }
        }

        if (treasureSteps == maxTreasureSteps) {
            setTreasurePositionRandom();
        }

        if (treasureY == gridHeight - 1) {
            treasureOutOfBounds = true;
        }

        // Last action: set treasure to the grid
        levelGrid[treasureX][treasureY] = TREASURE_BLOCK;
    }

    /**
     * <p>Updates the player.</p>
     * <p>Triggers game end or player death</p>
     */
    private void updatePlayer() {
        // Check if player has reached the bottom and therefore dies
        if (playerY == gridHeight - 1) {
            this.playerDead = true;
            logger.log(Level.FINE, "Player died at the bottom");
            playPlayerDeadAnimation();
            // Make the player "fall" if nothing is beneath it
        } else if (playerY < gridHeight - 1 && playerY != 0) {
            if (isNonStaticBlock(levelGrid[playerX][playerY + 1])) {
                setPlayerPosition(playerX, playerY + 1);
            }
        }

        // Trigger game end or next level if treasure is reached
        if (playerX == treasureX && playerY == treasureY) {
            this.treasureReached = true;
            logger.log(Level.INFO, "Player reached treasure");
            this.playTreasureFoundAnimation();
        }

        levelGrid[playerX][playerY] = PLAYER_BLOCK;
    }

    // #########################################################################
    // POSITIONING
    // #########################################################################

    /**
     * Sets the player position to new coordinates if possible
     *
     * @param x New x coordinate
     * @param y New y coordinate
     */
    private void setPlayerPosition(int x, int y) {
        if (x < gridWidth && x >= 0 && y < gridHeight && y >= 0) {
            playerX = x;
            playerY = y;
        }
    }

    /**
     * Sets the treasure position to new coordinates if possible
     *
     * @param x New x coordinate
     * @param y New y coordinate
     */
    private void setTreasurePosition(int x, int y) {
        if (x < gridWidth && x >= 0 && y < gridHeight && y >= 0) {
            treasureX = x;
            treasureY = y;
        }
    }

    /**
     * Sets the treasure to a random position and ensures for that position to
     * be empty
     */
    private void setTreasurePositionRandom() {
        logger.log(Level.FINE, "Moving treasure to random position");
        treasureSteps = 0;
        treasureOutOfBounds = false;
        boolean positionFound = false;
        int x = random.nextInt(gridWidth);
        int y = random.nextInt(gridHeight - 2);

        // Find a free position
        while (!positionFound) {
            x = random.nextInt(gridWidth);
            y = random.nextInt(gridHeight - 2);
            if (isStaticBlock(levelGrid[x][y + 1])) {
                if (levelGrid[x][y] != PLAYER_BLOCK) {
                    positionFound = true;
                }
            }
        }
        setTreasurePosition(x, y);
    }

    /**
     * Moves the player one pixel to the left
     */
    public void movePlayerLeft() {
        setPlayerPosition(playerX - 1, playerY);
    }

    /**
     * Moves the player one pixel to the right
     */
    public void movePlayerRight() {
        setPlayerPosition(playerX + 1, playerY);
    }

    /**
     * Checks if a block is static meaning that it belongs to the "background"
     * and doesn't move frequently
     *
     * @param block A specific block represented by an integer.
     * @return true if block is static
     */
    private boolean isStaticBlock(int block) {
        if (block == NORMAL_BLOCK) {
            return true;
        } else if (block == FADING_BLOCK) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if a block is non-static meaning that it can move more often than
     * the level "background"
     *
     * @param block A specific block represented by an integer.
     * @return true if block is non-static
     */
    private boolean isNonStaticBlock(int block) {
        if (block == EMPTY_BLOCK) {
            return true;
        } else if (block == PLAYER_BLOCK) {
            return true;
        } else if (block == TREASURE_BLOCK) {
            return true;
        } else {
            return false;
        }
    }

    // #########################################################################
    // ANIMATIONS
    // #########################################################################

    private void playTreasureFoundAnimation() {
        if (!animationActive) {
            logger.log(Level.INFO, "Starting treasure-found-animation");
            animationActive = true;
            levelGrid[playerX][playerY] = PLAYER_BLOCK;

            // Start animation
            Thread treasureAnimation = new Thread(new Runnable() {

                @Override
                public void run() {

                    try {
                        if (animationActive) {
                            // Clear levelGrid
                            for (int i = 0; i < gridWidth; i++) {
                                for (int j = 0; j < gridHeight; j++) {
                                    if (isStaticBlock(levelGrid[i][j])) {
                                        if (animationActive) {
                                            levelGrid[i][j] = EMPTY_BLOCK;
                                        }
                                        Thread.sleep(ANIMATION_DELAY);
                                    }
                                }
                            }
                        } else {
                            Thread.currentThread().interrupt();
                        }

                        if (animationActive) {

                            // Fill gold
                            for (int i = 0; i < gridHeight; i++) {
                                for (int j = 0; j < gridWidth; j++) {
                                    if (levelGrid[j][i] == EMPTY_BLOCK) {
                                        if (animationActive) {
                                            levelGrid[j][i] = TREASURE_BLOCK;
                                        }

                                        Thread.sleep(ANIMATION_DELAY);
                                    }
                                }
                            }

                        } else {
                            Thread.currentThread().interrupt();
                        }
                    } catch (InterruptedException e) {
                        logger.log(Level.WARNING,
                                "Treasure-Animationthread interrupted (clear)"
                                        + e);
                    }
                    animationActive = false;
                    logger.log(Level.FINE,
                            "Treasure-reached animation finished");
                    advanceLevel();
                }

            });
            animationRunning.add(treasureAnimation);
            treasureAnimation.start();
        }
    }

    private void playPlayerDeadAnimation() {
        if (!animationActive) {
            logger.log(Level.INFO, "Starting player-dead-animation");
            animationActive = true;

            Thread playerDeadAnimation = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        for (int i = gridHeight - 1; i >= 0; i--) {
                            for (int j = gridWidth - 1; j >= 0; j--) {

                                if (animationActive) {
                                    levelGrid[j][i] = RED_BLOCK;
                                    Thread.sleep(ANIMATION_DELAY);
                                } else {
                                    Thread.currentThread().interrupt();
                                }
                            }

                        }
                        animationActive = false;
                        playPlaceholderAnimation();
                    } catch (InterruptedException e) {
                        logger.log(Level.WARNING,
                                "Player-dead animationthread interrupted"
                                        + e);
                    }
                    logger.log(Level.FINE, "Player-dead animation finished");
                }

            });
            animationRunning.add(playerDeadAnimation);
            playerDeadAnimation.start();

        }
    }

    private void playPlaceholderAnimation() {
        // Init animation
        if (!animationActive) {
            logger.log(Level.INFO, "Starting placeholder-animation");
            animationActive = true;
            levelGrid = new int[gridWidth][gridHeight];

            Thread placeholderAnimation = new Thread(new Runnable() {

                @Override
                public void run() {
                    while (animationActive) {
                        for (int i = 0; i < gridWidth; i++) {
                            for (int j = 0; j < gridHeight; j++) {
                                try {
                                    if (animationActive) {
                                        if (levelGrid[i][j] == EMPTY_BLOCK) {
                                            levelGrid[i][j] = FADING_BLOCK;
                                        } else {
                                            levelGrid[i][j] = EMPTY_BLOCK;
                                        }

                                        Thread.sleep(ANIMATION_DELAY);
                                    } else {
                                        Thread.currentThread().interrupt();
                                    }
                                } catch (InterruptedException e) {
                                    logger.log(Level.WARNING,
                                            "Placeholder-Animationthread interrupted"
                                                    + e);
                                }
                            }
                        }
                    }
                    logger.log(Level.FINE, "Placeholder animation finished");
                }

            });
            animationRunning.add(placeholderAnimation);
            placeholderAnimation.start();
        }

    }

    private void playGameEndAnimation() {
        // Init animation
        if (!animationActive) {
            logger.log(Level.INFO, "Starting gameEnd-animation");
            animationActive = true;
            levelGrid = new int[gridWidth][gridHeight];

            Thread gameEndAnimation = new Thread(new Runnable() {

                @Override
                public void run() {
                    Random r = new Random();
                    while (animationActive) {
                        int randWidth = r.nextInt(gridWidth);
                        int randHeight = r.nextInt(gridHeight);
                        try {
                            if (animationActive) {
                                if (levelGrid[randWidth][randHeight] != RANDOM_BLOCK) {
                                    levelGrid[randWidth][randHeight] = RANDOM_BLOCK;
                                } else {
                                    levelGrid[randWidth][randHeight] = EMPTY_BLOCK;
                                }


                                Thread.sleep(ANIMATION_DELAY * 3);
                            } else {
                                Thread.currentThread().interrupt();
                            }
                        } catch (InterruptedException e) {
                            logger.log(Level.WARNING,
                                    "GameEnd-Animationthread interrupted"
                                            + e);
                        }
                    }

                    logger.log(Level.FINE, "GameEnd animation finished");
                }

            });
            animationRunning.add(gameEndAnimation);
            gameEndAnimation.start();
        }

    }


    /**
     * Ends any animation running to prevent bugs
     */
    private void endAllAnimations() {
        logger.log(Level.FINE, "Ending Animations");
        for (Thread animation : animationRunning) {
            animation.interrupt();
        }
        animationActive = false;

    }

    // #########################################################################
    // ACCESSORS
    // #########################################################################

    /**
     * Used to obtain the latest level including the player
     *
     * @return current version of the level
     */
    public int[][] getLevelGrid() {
        return levelGrid;
    }


    public void playIdleAnimation(){
        this.playPlaceholderAnimation();
    }

    /**
     * @return true if player is dead
     */
    public boolean isPlayerDead() {
        return this.playerDead;
    }

    /**
     * Indicates whether the game is still running
     *
     * @return true if a game is active
     */
    public boolean isActive() {
        return gameActive;

    }

}

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.sql.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;


public class JetFighter extends JFrame {
    //2 states, either you are in the menu or the game
    //Class Menu handles most things related to the menu state
    private Menu menuPanel;
    //Class Game handles most things related to the game state
    private Game gamePanel;

    //Cardlayout to switch between the states
    private CardLayout stateCardLayout;

    //A server to handle communication between clients
    private Server server;
    //A connection to the database for sql queries
    private Connection dbConnection;

    /**
     * Constructor, initialize the program
     */
    JetFighter(){
        initialize();
    }

    /**
     * initialize the program
     */
    private void initialize(){
        //set up the connection to database
        startDbConnection();
        //set up the menu screen
        menuPanel = new Menu(this);
        //set up the cardlayout to switch between screens
        stateCardLayout = new CardLayout();
        getContentPane().setLayout(stateCardLayout);
        //add the menu panel to this frames content pane
        getContentPane().add(menuPanel, "menu");
        stateCardLayout.show(this.getContentPane(), "menu");

        //set different parameters for the window
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(640, 480);
        setFocusable(true);
        setVisible(true);
    }

    /**
     * get the game panel
     * @return the game panel
     */
    public Game getGame(){
        return gamePanel;
    }

    /**
     * get the database connection
     * @return the database connection
     */
    public Connection getDbConnection(){return dbConnection;}

    private Server setAndGetServer(int myPort, String myHost, boolean mainServer){
        //create a server for handling communication between clients
        server = new Server(this, myPort, myHost, mainServer);
        //once a server is created we can set up the game panel
        gamePanel = new Game(this, server);
        getContentPane().add(gamePanel, "game");
        //start a new thread on the server we created
        new Thread(server).start();
        //return the server
        return server;
    }

    /**
     * try and connect to the database and save the connection in the
     * dbConnection variable
     */
    private void startDbConnection(){
        try {
            Class.forName("com.mysql.jdbc.Driver");
            String computer = "atlas.dsv.su.se";
            String db_name = "db_21215895";
            String url = "jdbc:mysql://" + computer + "/" + db_name;
            String username = "usr_21215895";
            String password = "215895";
            dbConnection = DriverManager.getConnection(url, username, password);
            Statement stmt = dbConnection.createStatement();
            if (!tableExist("Saved_Scores")) { //if table does not exist create it
                stmt.execute(
                        "CREATE TABLE Saved_Scores("
                                + "ID INT NOT NULL AUTO_INCREMENT, "
                                + "WhiteScore INT NOT NULL, "
                                + "BlackScore INT NOT NULL, "
                                + "PRIMARY KEY (ID))"
                );
            }
        } catch(Exception e){
            System.err.println(e);
        }
    }

    /**
     * Check if a table exists in our database
     * @param table the name of the table to check if it exists
     * @return true if it exists, false if it does not
     */
    private boolean tableExist(String table){
        try{
            ResultSet rs = getDbConnection().getMetaData().getTables(null, null, table, new String[] {"TABLE"}); //returns any table with the name stored in variable table
            return rs.next(); //returns false if rs is empty, which it would be if no table was found by the given name, or true if it did find a table and therefore has a value stored
        }catch(Exception e){
            System.err.println(e);
            return false;       //return false if the the connection failed for any reason
        }

    }

    /**
     * main method, called when execution starts on this program
     * @param args not used
     */
    public static void main(String[] args){
        //create an instance of the JetFighter class which contains our game
        new JetFighter();
    }

    ////////////////////////////////////////////////  SERVER  ////////////////////////////////////////////////////////////

    class Server implements Runnable {
        //save a reference to the JetFighter instance
        private JetFighter mainFrame;
        //the socket which communication is handled through
        private DatagramSocket socket;

        //save communication information
        private int myPort;
        private String myHost;
        private int remotePort;
        private String remoteHost;

        //check if we have found a "partner"
        private boolean playerFound = false;
        //check if this is the "main" server and therefore handle some extra communication
        private boolean mainServer = false;
        //stops the thread execution till both clients have set up their communication
        private boolean waitingForConnection = true;

        /**
         * Constructor, initializes variables
         * @param mainFrame Reference to the JetFighter instance
         * @param myPort This clients port to run the socket on
         * @param myHost This clients host address
         * @param mainServer true if this is the main server, false if not
         */
        public Server(JetFighter mainFrame, int myPort, String myHost, boolean mainServer){
            this.mainFrame = mainFrame;
            this.myPort = myPort;
            this.myHost = myHost;
            this.mainServer = mainServer;
            try{
                this.socket = new DatagramSocket(myPort);
            }catch(Exception e){
                System.err.println(e);
            }
        }

        /**
         * @return if this server is the main server
         */
        public boolean isMainServer(){
            return mainServer;
        }

        /**
         * Try and connect to another server
         * @param remoteHost the host address to try and connect to
         * @param remotePort the port to try and connect to
         */
        public void connect(String remoteHost, int remotePort){
            try{
                //send a message containing this server host address and port to the remote client
                String message = myHost + " " + myPort;
                byte[] data = message.getBytes();
                InetAddress remoteAddress = InetAddress.getByName(remoteHost);
                DatagramPacket sendPackage = new DatagramPacket(data, data.length, remoteAddress, remotePort);
                socket.send(sendPackage);

                //wait for response
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePackage);
                String receiveString = new String(receivePackage.getData(), 0, receivePackage.getLength());
                String[] remoteData = receiveString.split(" ");
                try {
                    //when we get a response, set the remoteHost and remotePort variables to given values
                    this.remoteHost = remoteData[0];
                    this.remotePort = Integer.parseInt(remoteData[1]);
                }catch(Exception er){
                    System.err.println(er);
                }
                waitingForConnection = false; //tell the thread to stop waiting
            }catch(Exception e){
                System.err.println(e);
            }
        }

        /**
         * Method used for communication with the remote client
         * @param content the type of content we are sending, example("Position" when we are sending position information)
         * @param x Depending on the content type this can be used for coordinates, port number or score number.
         * @param y Depending on the content type this can be used for coordinates, port number or score number.
         */
        public void sendInfo(String content, int x, int y){
            try{
                String message = content + " " + x + " " + y;
                byte[] data = message.getBytes();
                InetAddress remoteAddress = InetAddress.getByName(remoteHost);
                DatagramPacket sendPackage = new DatagramPacket(data, data.length, remoteAddress, remotePort);
                socket.send(sendPackage);
            }catch(Exception e){
                System.err.println(e);
            }
        }

        /**
         * this method loops on a separate thread
         * Sets up initial communication paths
         * after initial communication paths are set up, wait for incoming communication on infinite loop
         */
        @Override
        public void run() {
            try {
                //wait for player to join if this is the main server (Pressed new game and waiting for someone to join through join game)
                while (!playerFound && mainServer) {
                    //wait till received a package
                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePackage);
                    String receiveString = new String(receivePackage.getData(), 0, receivePackage.getLength());
                    String[] remoteData = receiveString.split(" ");
                    try {
                        //set the remote address and port to the given values
                        remoteHost = remoteData[0];
                        remotePort = Integer.parseInt(remoteData[1]);
                        //we found a remote client to play with
                        playerFound = true;
                    }catch(Exception er){
                        System.err.println(er);
                    }
                    //send back info
                    sendInfo(myHost, myPort, 0);
                    //no longer waiting for a remote connection
                    waitingForConnection = false;
                }
                //loop here till connection is set up
                while(waitingForConnection){

                }
                //player is found if past here
                //switch panel to game
                stateCardLayout.show(mainFrame.getContentPane(), "game");

                while(true){ //wait for data
                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePackage = new DatagramPacket(receiveData, receiveData.length);
                    //wait for data
                    socket.receive(receivePackage);
                    String receiveString = new String(receivePackage.getData(), 0, receivePackage.getLength());
                    String[] remoteData = receiveString.split(" ");
                    //check what type of information was given
                    if(remoteData[0].equals("Position")){
                        //set position of remote clients jet to incoming values
                        mainFrame.getGame().setEnemyPosition(Integer.parseInt(remoteData[1]), Integer.parseInt(remoteData[2]));
                    }else if(remoteData[0].equals("Shot")){
                        //spawn a shot with given position
                        mainFrame.getGame().setEnemyBullet(Integer.parseInt(remoteData[1]), Integer.parseInt(remoteData[2]));
                    }else if(remoteData[0].equals("Score")){
                        //set the score to given value (Main server tracks score)
                        mainFrame.getGame().setScore(Integer.parseInt(remoteData[1]), Integer.parseInt(remoteData[2]));
                    }
                }

            }catch(Exception e){
                System.err.println(e);
            }
        }
    }

    ///////////////////////////////////////////// Game /////////////////////////////////////////////////////////////

    class Game extends JPanel implements Runnable{
        //Reference to JetFighter instance
        private JetFighter mainFrame;
        //File paths for images              <EDIT THESE PATHS>
        private final String whiteJetPath = "D:/kod/IPROG/GisProv/src/FighterJets/WhiteJet.png";
        private final String blackJetPath = "D:/kod/IPROG/GisProv/src/FighterJets/BlackJet.png";
        private final String bulletPath = "D:/kod/IPROG/GisProv/src/FighterJets/Bullet.png";
        //Tracks which color this client is
        private boolean isWhite;
        //Holds the images used in the game
        private Image whiteJet;
        private Image blackJet;
        private Image bullet;
        //Server to handle communication
        private Server server;
        //My jet instance
        private Jet myJet;
        //Enemy jet instance
        private Jet enemyJet;
        //Labels that display score
        private JLabel whiteScoreLabel = new JLabel("White score: 0");
        private JLabel blackScoreLabel = new JLabel ("Black score: 0");
        //button to save the current score
        private JButton saveScore = new JButton("Save score");
        //track the current score
        private int whiteScore = 0;
        private int blackScore = 0;
        //Sizing for the images
        private int jetWidth = 20;
        private int jetHeight= 20;
        private int bulletSize = 5;
        //Holds all the bullets fired
        private HashSet<Bullet> bullets = new HashSet();
        //Tracks if left or right is pressed and not released yet
        private boolean leftIsPressed = false;
        private boolean rightIsPressed = false;

        /**
         * Constructor for setting up the game environment
         * @param mainFrame Reference to the JetFighter instance
         * @param server The server for communication with remote client
         */
        public Game(JetFighter mainFrame, Server server) {
            this.mainFrame = mainFrame;
            this.server = server;
            //if this server is the main server, we are white, otherwise black.
            if(server.isMainServer()){
                isWhite = true;
            }else{
                isWhite = false;
            }
            //Try and read the images from given file paths and scale them to wanted size
            try {
                whiteJet = ImageIO.read(new File(whiteJetPath));
                blackJet = ImageIO.read(new File(blackJetPath));
                bullet = ImageIO.read(new File(bulletPath));
                whiteJet = whiteJet.getScaledInstance(jetWidth, jetHeight, Image.SCALE_DEFAULT);
                blackJet = blackJet.getScaledInstance(jetWidth, jetHeight, Image.SCALE_DEFAULT);
                bullet = bullet.getScaledInstance(bulletSize, bulletSize, Image.SCALE_DEFAULT);
            }catch(Exception e){
                System.err.println(e);
            }
            //if im white, my jet is white otherwise black, opposite for the enemy
            if(isWhite) {
                myJet = new Jet(whiteJet, true);
                enemyJet = new Jet(blackJet, false);
            }else{
                myJet = new Jet(blackJet, false);
                enemyJet = new Jet(whiteJet, true);
            }
            //set background color to light gray
            setBackground(Color.lightGray);
            //add score labels and save score button to this panel
            add(whiteScoreLabel);
            add(blackScoreLabel);
            //listen for button clicks and call method saveScorePressed if click found
            saveScore.addActionListener(this::saveScorePressed);
            add(saveScore);
            //Listen for key presses
            mainFrame.addKeyListener(new MyKeyListener());
            //start a new thread on this instance
            new Thread(this).start();
        }

        /**
         * Called when the save score button is pressed, Saves the current score to the database
         * @param ae The event that occurred, not used in this method
         */
        private void saveScorePressed(ActionEvent ae) {
            try {
                String query = "INSERT INTO Saved_Scores (WhiteScore, BlackScore) VALUES (?, ?)";
                PreparedStatement pStmt = mainFrame.getDbConnection().prepareStatement(query);
                pStmt.setString(1, String.valueOf(whiteScore));
                pStmt.setString(2, String.valueOf(blackScore));
                pStmt.execute();                                        //Add the row to the database
            } catch(Exception e){
                System.err.println(e);
            }
        }


        /**
         * Called through the keylistener when 'a' is pressed or released
         * Makes sure the game knows a has been pressed or released and it should move or stop moving my jet to the left
         * @param isPressed true if 'a' has been pressed, false if it has been released
         */
        public void setLeftIsPressed(boolean isPressed){
            leftIsPressed = isPressed;
        }
        /**
         * Called through the keylistener when 'd' is pressed or released
         * Makes sure the game knows a has been pressed or released and it should move or stop moving my jet to the right
         * @param isPressed true if 'd' has been pressed, false if it has been released
         */
        public void setRightIsPressed(boolean isPressed){
            rightIsPressed = isPressed;
        }

        /**
         * Called through the keylistener when space bar is pressed
         * Tells the server to communicate that this client has shot a bullet
         * Create a new instance of Bullet from my jets current position and
         * add it to the list of bullets spawned so that the separate thread can draw it on the screen
         */
        public synchronized void shoot(){
            //tell the server that a shot has been shot
            sendShot();
            Bullet newBullet;
            //check which color we are to make sure we shoot from the correct jet
            if(isWhite)
                newBullet = new Bullet(bullet, myJet.x+10, myJet.y+25, false);
            else
                newBullet = new Bullet(bullet, myJet.x+10, myJet.y-25, true);

            //add the bullet to the list of bullets
            bullets.add(newBullet);

        }

        /**
         * Create a new instance of Bullet from given coordinates and tell them to travel in the direction of my jet
         * and add it to the list of bullets spawned so that the separate thread can draw it on the screen
         * @param x the x coordinate to spawn the bullet at
         * @param y the y coordinate to spawn the bullet at
         */
        public synchronized  void setEnemyBullet(int x, int y){
            Bullet newBullet;
            //check which color we are to make sure we shoot from the correct jet
            if(isWhite)
                newBullet = new Bullet(bullet, x+10, y-25, true);
            else
                newBullet = new Bullet(bullet, x+10, y+25, false);
            //add the bullet to the list of bullets
            bullets.add(newBullet);
        }

        /**
         * Updates the position of the enemy with given x, y coordinates
         * @param x the x coordinate to move the jet to
         * @param y the y coordinate to move the jet to
         */
        public synchronized void setEnemyPosition(int x, int y){
                enemyJet.x = x;
                enemyJet.y = y;
                //tell the program to redraw the images
                repaint();
        }

        /**
         * Changes the current score to the given values
         * @param whiteScore the score to set the whites score to
         * @param blackScore the score to set the black score to
         */
        public synchronized void setScore(int whiteScore, int blackScore){
            //update variables tracking the score
            this.whiteScore = whiteScore;
            this.blackScore = blackScore;
            //update the labels showing the score to the user
            whiteScoreLabel.setText("White score: " + whiteScore);
            blackScoreLabel.setText("Black score: " + blackScore);
        }

        /**
         * Responsible for drawing the images on the screen
         * @param g the graphics component used to draw
         */
        public synchronized void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(myJet.getImage(), myJet.getX(), myJet.getY(),this);
            g.drawImage(enemyJet.getImage(), enemyJet.getX(), enemyJet.getY(),this);
            Iterator i = bullets.iterator();
            while(i.hasNext()) {
                Bullet b = (Bullet)i.next();
                g.drawImage(b.getImage(), b.getX(), b.getY(), this);
            }
        }

        /**
         * tell the server to send my jets position to the enemy player
         */
        private void sendJetPosition(){
            server.sendInfo("Position", myJet.getX(), myJet.getY());
        }

        /**
         * tell the server to send to the enemy player that a shot has been shot at my jets current position
         */
        private void sendShot(){
            server.sendInfo("Shot", myJet.getX(), myJet.getY());
        }

        /**
         * Tell the server to send that to the enemy player that the score has changed and send the new values
         */
        private void sendUpdatedScore(){
            server.sendInfo("Score", whiteScore, blackScore);
        }

        /**
         * Code that a separate thread runs on infinite loop
         * updates my jets position every loop if it should be
         * Moves the bullets spawned every loop
         * If this client holds the main server, it also checks for bullet hits to jets
         */
        @Override
        public void run() { //separate thread runs this
            while(true){
                try{
                    //move my jet to the left if 'a' is pressed and it is not at the edge of the screen
                    if(leftIsPressed && myJet.getX()>0) {
                        myJet.x -= 1;
                        //tell the enemy player that my jet had moved
                        sendJetPosition();
                    }
                    //move my jet to the right if 'd' is pressed and it is not at the edge of the screen
                    if(rightIsPressed && myJet.getX()<605) {
                        myJet.x += 1;
                        //tell the enemy player that my jet had moved
                        sendJetPosition();
                    }
                    synchronized (this){
                        //loop through all bullets spawned
                        for(Bullet b: bullets){
                            //tell each bullet to move
                            b.moveBullet();
                            //if the bullet has not already hit something and this server is the main server
                            if(!b.getExploded() && server.mainServer) {
                                //check if this bullet hits my jet
                                if (myJet.getX() > b.getX() - 24 && myJet.getX() < b.getX() + 5)//within x range of bomb
                                    if (myJet.getY() > b.getY() - 24 && myJet.getY() < b.getY() + 5) {//within y range of bomb
                                        //bomb hits, add score and set the bullet to exploded
                                        b.setExploded();
                                        //add the score to the correct person
                                        if(isWhite){
                                            blackScore++;
                                            blackScoreLabel.setText("Black score: " + blackScore);
                                            //tell the server that the score has changed and to communicate this to the other player
                                            sendUpdatedScore();
                                        }else{
                                            whiteScore++;
                                            whiteScoreLabel.setText("White score: " + whiteScore);
                                            //tell the server that the score has changed and to communicate this to the other player
                                            sendUpdatedScore();
                                        }
                                    }
                                //check if this bullet hits enemy jet
                                if (enemyJet.getX() > b.getX() - 24 && enemyJet.getX() < b.getX() + 5)//within x range of bomb
                                    if (enemyJet.getY() > b.getY() - 24 && enemyJet.getY() < b.getY() + 5) {//within y range of bomb
                                        //bomb hits, add score and set the bullet to exploded
                                        b.setExploded();
                                        //add the score to the correct person
                                        if(!isWhite){
                                            blackScore++;
                                            blackScoreLabel.setText("Black score: " + blackScore);
                                            //tell the server that the score has changed and to communicate this to the other player
                                            sendUpdatedScore();
                                        }else{
                                            whiteScore++;
                                            whiteScoreLabel.setText("White score: " + whiteScore);
                                            //tell the server that the score has changed and to communicate this to the other player
                                            sendUpdatedScore();
                                        }
                                    }
                            }
                        }
                    }
                    //redraw the images
                    repaint();
                    //wait 10ms
                    Thread.sleep(10);
                }catch(Exception e){
                    System.err.println(e);
                }
            }
        }


        //inner class to Game class which is used to listen for user input to play the game
        public class MyKeyListener implements KeyListener {
            /**
             * Not used
             * @param e Not used
             */
            @Override
            public void keyTyped(KeyEvent e) {

            }

            /**
             * Called when a key is pressed, if the key pressed is 'a' or 'd', tell the game that it is currently pressed
             * if space bar is pressed tell the game to shoot a bullet
             * @param e the KeyEvent that occurred
             */
            @Override
            public void keyPressed(KeyEvent e) {
                char ch = e.getKeyChar();
                if(ch == 'a')
                    setLeftIsPressed(true);
                if(ch == 'd')
                    setRightIsPressed(true);
                if(e.getKeyCode() == KeyEvent.VK_SPACE)
                    shoot();

            }

            /**
             * Called when a key is released, if the key released is 'a' or 'd', tell the game that it is no longer pressed
             * @param e the KeyEvent that occurred
             */
            @Override
            public void keyReleased(KeyEvent e) {
                char ch = e.getKeyChar();
                if(ch == 'a')
                    setLeftIsPressed(false);
                if(ch == 'd')
                    setRightIsPressed(false);

            }
        }

        //inner class to Game class, each instance handles its own bullet shot by a player
        class Bullet {
            //Holds the image of the bullet
            private Image bulletImage;
            //Holds the current coordinates of the bullet
            private int x;
            private int y;
            //Tracks the direction the bullet is moving
            private boolean goingUp;
            //Sets the speed the bullet should move at
            private int speed = 4;
            //tracks if the bullet has already hit a jet
            private boolean exploded = false;

            public Bullet(Image bullet, int x, int y, boolean goingUp){
                this.bulletImage = bullet;
                this.goingUp = goingUp;
                this.x = x;
                this.y = y;
            }

            /**
             * Move the bullet in the direction it should be moving at the speed set
             */
            public void moveBullet(){
                if(goingUp)
                    y-=speed;
                else
                    y+=speed;
            }

            public void setExploded(){
                exploded = true;
            }
            public boolean getExploded(){
                return exploded;
            }
            public Image getImage(){
                return bulletImage;
            }
            public int getX(){
                return this.x;
            }
            public int getY(){
                return this.y;
            }
        }

        //inner class to Game class, one instance for each player to track their jet
        class Jet {
            //Holds the image of the jet
            private Image jetImage;
            //Holds the current coordinates of the jet
            private int x;
            private int y;

            public Jet(Image jet, boolean isTop){
                this.jetImage = jet;
                //make sure to spawn the jet on the correct side
                if(isTop){
                    y = 50;
                }else{
                    y = 375;
                }
                x = 250;
            }

            public Image getImage(){
                return jetImage;
            }
            public int getX(){
                return this.x;
            }
            public int getY(){
                return this.y;
            }
        }
    }

    //////////////////////////////////////////////////////// Menu ///////////////////////////////////////////////////

    class Menu extends JPanel{
        //The first screen that is shown
        private final HomeScreen homeScreen;
        //the screen shown when new game is pressed
        private final NewGameScreen newGameScreen;
        //the screen shown when join game is pressed
        private final JoinGameScreen joinGameScreen;
        //the screen shown when saved scores is pressed
        private final SavedScoresScreen savedScoresScreen;

        //the cardlayout to handle showing the correct screen to the user
        private final CardLayout menuCardLayout;


        public Menu(JetFighter mainFrame){
            //create instances of each screen
            homeScreen = new HomeScreen(this);
            newGameScreen = new NewGameScreen( this);
            joinGameScreen = new JoinGameScreen(this);
            savedScoresScreen = new SavedScoresScreen(mainFrame, this);

            //set up the cardlayout with each screen
            menuCardLayout = new CardLayout();
            setLayout(menuCardLayout);
            add(homeScreen, "home");
            add(newGameScreen, "new");
            add(joinGameScreen, "join");
            add(savedScoresScreen, "score");
            //show the home screen
            menuCardLayout.show(this, "home");
        }




        //inner class to Menu, this is the initial page you will see when the program is launched
        class HomeScreen extends JPanel{
            //Reference to the parent panel
            private JPanel parent;

            //Buttons to trigger context switches
            private JButton newGame = new JButton("New game");
            private JButton joinGame = new JButton("Join game");
            private JButton ScoreButton = new JButton("View saved scores");

            /**
             * Constructor, initializes buttons
             * @param parent
             */
            public HomeScreen(JPanel parent){
                this.parent = parent;
                newGame.addActionListener(this::newGamePressed);
                joinGame.addActionListener(this::joinGamePressed);
                ScoreButton.addActionListener(this::scorePressed);
                add(newGame);
                add(joinGame);
                add(ScoreButton);
            }

            private void newGamePressed(ActionEvent ae){
                menuCardLayout.show(parent, "new");
                //switch to new game panel (card layout)
            }
            private void joinGamePressed(ActionEvent ae){
                menuCardLayout.show(parent, "join");
                //switch to join game panel (card layout)
            }
            private void scorePressed(ActionEvent ae){
                menuCardLayout.show(parent, "score");
                //switch to view score panel (card layout)
            }
        }

        //inner class to Menu, this is the page shown after the new game button is pressed
        class NewGameScreen extends JPanel{
            //Reference to the parent panel
            private final JPanel parent;
            //labels and text fields to enter port and host address
            private final JLabel myPortLabel = new JLabel("My port: ");
            private final JTextField myPortField = new JTextField(16);
            private final JLabel myHostLabel = new JLabel("My host address: ");
            private final JTextField myHostField = new JTextField(16);
            //Button for setting up the server for communication with the entered values
            private final JButton setUpServer = new JButton("Set server");
            //Button to go back to home screen
            private final JButton backButton = new JButton("Back");

            /**
             * Initialize all labels, textfields and buttons
             * @param parent the parent panel
             */
            public NewGameScreen(JPanel parent){
                this.parent = parent;
                add(new JLabel("Waiting for another player"));

                add(myPortLabel);
                add(myPortField);
                add(myHostLabel);
                add(myHostField);
                setUpServer.addActionListener(this::setServerPressed);
                add(setUpServer);
                backButton.addActionListener(this::backPressed);
                add(backButton);
            }

            /**
             * Called when set server button is pressed, initializes the server with the values in the text fields
             * and since we are creating a new game we are also specifying this server will be the main server
             * @param ae The event that occurred (Not used)
             */
            private void setServerPressed(ActionEvent ae){
                try {
                    setAndGetServer(Integer.parseInt(myPortField.getText()), myHostField.getText(), true);
                }catch(Exception e){
                    System.err.println(e);
                }
            }

            /**
             * Called when the back button is pressed, switches the screen back to the home screen
             * @param ae The Event that occurred (Not used)
             */
            private void backPressed(ActionEvent ae){
                menuCardLayout.show(parent, "home");
            }
        }


        //inner class to menu, this is the screen shown after the join game button is pressed
        class JoinGameScreen extends JPanel{
            //Reference to the parent panel
            private final JPanel parent;
            //Labels and text fields to enter port and host address information
            private final JLabel myPortLabel = new JLabel("My port: ");
            private final JTextField myPortField = new JTextField(16);
            private final JLabel myHostLabel = new JLabel("My host address: ");
            private final JTextField myHostField = new JTextField(16);
            private final JLabel remotePortLabel = new JLabel("Remote port: ");
            private final JTextField remotePortField = new JTextField(16);
            private final JLabel remoteHostLabel = new JLabel("Remote Host: ");
            private final JTextField remoteHostField = new JTextField(16);
            //Button to set up the server and attempt connection with remote client
            private final JButton setUpServer = new JButton("Attempt connection");
            //Button to go back to home screen
            private final JButton backButton = new JButton("Back");

            /**
             * Constructor, initializes all labels, text fields and buttons
             * @param parent
             */
            public JoinGameScreen(JPanel parent){
                this.parent = parent;
                add(new JLabel("JoinGame"));
                add(myPortLabel);
                add(myPortField);
                add(myHostLabel);
                add(myHostField);
                add(remotePortLabel);
                add(remotePortField);
                add(remoteHostLabel);
                add(remoteHostField);
                setUpServer.addActionListener(this::setServerPressed);
                add(setUpServer);
                backButton.addActionListener(this::backPressed);
                add(backButton);
            }

            /**
             * Called when Attempt connection button is pressed
             * Initializes the server with given values and tries to connect with remote client through this server
             * @param ae the event that occurred (Not used)
             */
            private void setServerPressed(ActionEvent ae){
                try {
                    Server server = setAndGetServer(Integer.parseInt(myPortField.getText()), myHostField.getText(), false);
                    server.connect(remoteHostField.getText(), Integer.parseInt(remotePortField.getText()));
                }catch(Exception e){
                    System.err.println(e);
                }
            }

            /**
             * Called when the back button is pressed
             * Switches the screen back to the home page
             * @param ae The event that occurred (Not used)
             */
            private void backPressed(ActionEvent ae){
                menuCardLayout.show(parent, "home");
            }

        }


        //inner class to menu, this is the screen shown when the score button is pressed
        class SavedScoresScreen extends JPanel {
            //Reference to the JetFighter instance
            private final JetFighter mainFrame;
            //Reference to the parent panel
            private final JPanel parent;
            //Text area to show all saved scores in
            private JTextArea showDBText = new JTextArea(16, 16);
            //Button to go back to home screen
            private final JButton backButton = new JButton("Back");

            /**
             * Constructor, Initializes text area and button
             * Loads saved scores from database and displays them in the text area
             * @param mainFrame
             * @param parent
             */
            public SavedScoresScreen(JetFighter mainFrame, JPanel parent){
                this.mainFrame = mainFrame;
                this.parent = parent;
                //Listen for presses on this button
                backButton.addActionListener(this::backPressed);

                //add border around the text area
                Border border = BorderFactory.createLineBorder(Color.BLACK);
                showDBText.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(10, 10, 10, 10)));
                //add scroll functionality to the text area
                JScrollPane scroll = new JScrollPane (showDBText);
                scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
                scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
                //add the text area and button to the panel
                add(scroll);
                add(backButton);
                //Load the scores and display them
                loadAndPresentScores();
            }

            /**
             * Retrieves all saved scores from the database and displays them in the text area
             */
            private void loadAndPresentScores(){
                try {
                    //Retrieve saved scores from database
                    Statement stmt = mainFrame.getDbConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT * FROM Saved_Scores");
                    showDBText.setText("");
                    //Display each row retrieved in the text area
                    while (rs.next()) {
                        showDBText.append("ID: " + rs.getString("ID")
                                + " WhiteScore: " + rs.getString("WhiteScore")
                                + " BlackScore: " + rs.getString("BlackScore")+ "\n");
                    }
                } catch(Exception e){
                    System.err.println(e);
                }
            }

            /**
             * Called when the back button is pressed
             * Switches the screen back to the home screen
             * @param ae the event that occurred (Not used)
             */
            private void backPressed(ActionEvent ae){
                menuCardLayout.show(parent, "home");
            }

        }
    }
}


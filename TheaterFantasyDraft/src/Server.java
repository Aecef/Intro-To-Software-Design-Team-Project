import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server extends JFrame {
    private JTextArea displayArea;
    private JTextField enterField;
    private ExecutorService executor;
    private ServerSocket server;
    private SockServer[] sockServer;
    private int[] draftOrder;
    private int draftOrderPos = 0;
    private int counter = 1;
    private int nClientsActive = 0;
    private Draft d = new Draft();
    private boolean draftHappened, draftHappening = false;
    private String[] commmands = new String[]{"$draft", "$trade", "$roster", "$update", "$totalroster",
                                            "$infodraft", "$infotrade","$inforoster", "$infoupdate"};

    public Server() {
        super("Server");

        sockServer = new SockServer[20]; // allocate array for up to 20 Players
        executor = Executors.newFixedThreadPool(20); //20 Players Max

        enterField = new JTextField();
        //enterField.setEditable(false);
        enterField.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        for (int i = 1; i <= counter; i++) {
                            if (sockServer[i].alive)
                                sockServer[i].sendData(event.getActionCommand());
                        }
                        enterField.setText("");
                    }
                }
        );
        d.readFile();//Takes in the Roster from the file
        add(enterField, BorderLayout.SOUTH);
        displayArea = new JTextArea(); // create displayArea
        add(new JScrollPane(displayArea), BorderLayout.CENTER);

        setSize(500, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void runServer() {
        try
        {
            server = new ServerSocket(23555, 100);

            while (true) {
                try {
                    sockServer[counter-1] = new SockServer(counter);
                    sockServer[counter-1].waitForConnection();
                    nClientsActive++;
                    executor.execute(sockServer[counter-1]);

                }
                catch (EOFException eofException) {
                    displayMessage("\nServer terminated connection");
                }
                finally { ++counter;}
            }
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * MethodName: contains
     * Purpose: Determine whether the item is within the array or not
     *
     * @param a Array searched
     * @param i Item looked for within the array
     * @return True if i is within a, False otherwise
     */
    private boolean contains(int[] a , int i){
        for(int integer : a){
            if(integer == i){
                return true;
            }
        }
        return false;
    }

    /**
     * MethodName: messageToAll
     * Purpose: Takes in a message and sends it to all active clients
     *
     * @param message The message that will be displayed to all of the clients
     * @throws IOException
     */
    private void messageToAll(String message) throws IOException {
        for(SockServer s : sockServer){
            if(s != null && s.alive) {
                s.output.writeObject(message);
                s.output.flush();
            }
        }
    }

    private void rosterToAll() throws IOException {
        for(SockServer s : sockServer){
            if(s != null && s.alive) {
                s.sendRoster();
            }
        }
    }
    /**
     * MethodName: startDraft
     * Purpose: Determines the order of the draft and begins it by selecting the starting player
     * @throws IOException
     */
    private void startDraft() throws IOException {
        SecureRandom rand = new SecureRandom();
        this.draftOrder = new int[nClientsActive];
        int starterClient;
        for(int i = 0; i < nClientsActive; i++){
            starterClient = rand.nextInt(nClientsActive) + 1;
            if(!contains(draftOrder, starterClient)) {
                draftOrder[i] = starterClient;
            }
            else {i -= 1;}
        }

        for(int j = 0; j < draftOrder.length;j++){
            draftOrder[j] = (draftOrder[j] - 1);
            System.out.println("Current J: " + j);
            System.out.println("New Val: " + draftOrder[j]);
        }

        rosterToAll();
        messageToAll("\nPlayer " + (draftOrder[0] + 1) + " begins the draft!\n");
        sockServer[draftOrder[0]].setDraftTurn(true);
    }

    // manipulates displayArea in the event-dispatch thread
    private void displayMessage(final String messageToDisplay) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() // updates displayArea
                    {
                        displayArea.append(messageToDisplay); // append message
                    }
                }
        );
    }

    private void setTextFieldEditable(final boolean editable) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run()
                    {
                        enterField.setEditable(editable);
                    }
                }
        );
    }

    private class SockServer implements Runnable {
        private ObjectOutputStream output; // output stream to client
        private ObjectInputStream input; // input stream from client
        private Socket connection; // connection to client
        private int myConID;
        private boolean alive = false;
        private boolean draftTurn = false;
        private String[] playerRosterNames = new String[5];//Limits the player to having 5 Actors on their team
        private Draft.ActorData[] playerRoster = new Draft.ActorData[5];//Limits the player to having 5 Actors on their team
        private int draftCounter; //Tracks how many choices a player has made
        private boolean inTrade = false;
        private int tradeWith;
        private String actorToGet;
        private String myActorToGive;
        int traderPlayerSelection;




        public SockServer(int counterIn) {
            myConID = counterIn;
        }

        public void setDraftTurn(boolean draftTurn) {
            this.draftTurn = draftTurn;
        }
        public boolean getDraftTurn(){
            return this.draftTurn;
        }

        public void run() {
            try {
                alive = true;
                try {
                    getStreams(); // get input & output streams
                    processConnection();
                    nClientsActive--;
                }
                catch (EOFException eofException) {
                    displayMessage("\nServer " + myConID + " terminated connection");
                } finally {
                    closeConnection();
                }
            }
            catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        /**
         * MethodName: printRoster
         * Purpose: Prints the toString of all the Actors within the player's roster
         * @throws IOException
         */
        private void printRoster() throws IOException {
            for(Draft.ActorData aD : playerRoster)
                if(aD != null){
                    sendMessage("\nYOUR ROSTER\n" +
                            "--------------------\n" + aD.toString() +"\n");
                }
        }

        private void waitForConnection() throws IOException {

            displayMessage("Waiting for Player " + myConID + "\n");
            connection = server.accept(); // allow server to accept connection
            displayMessage("Connection of Player " + myConID + " received from: " +
                    connection.getInetAddress().getHostName());
        }



        /**
         * MethodName: getStreams
         * Purpose: Gets the streams and receives the data
         */
        private void getStreams() throws IOException {
            // set up output stream for objects
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush(); // flush output buffer to send header information
            // set up input stream for objects
            input = new ObjectInputStream(connection.getInputStream());
            displayMessage("\nGot I/O streams\n");
        }


        /**
         * MethodName: processConnection
         * Purpose: Takes in the received data. This helps to know what commands to run and how to update the rosters
         */
        private void processConnection() throws IOException {
            String message = "Connection from Player " + myConID + " successful";
            sendData(message); // send connection successful message
            sendMessage("\nYou are Player " + myConID + "!\n");
            // enable enterField so server user can send messages
            setTextFieldEditable(true);
            do
            {
                try
                {

                    message = (String) input.readObject(); // read new message
                    System.out.println(message);

                    doDraft(message);
                    for(String cmmd : commmands){
                        if(message.contains(cmmd)){
                            runCommand(cmmd);
                        }
                    }
                    // allows the user to submit draft request if it is their turn
                    // TO DO: Advance to next players turn and limit it to 5 choices

                    if (message.contains("$Trading")) {
                        System.out.println("Accepting trade");

                        try {
                            String tokens[] = message.split(" ");

                            String response = tokens[2];
                            int playerToSendTo = Integer.parseInt(tokens[3]);

                            sockServer[playerToSendTo-1].executeTrade();
                        }
                        catch (IndexOutOfBoundsException e) {
                            displayMessage("Invalid Input");
                        }
                    }

                    displayMessage("\n" + myConID + " " +  message);
                } // end try
                catch (ClassNotFoundException classNotFoundException) {
                    displayMessage("\nUnknown object type received");
                } // end catch

            } while (!message.equals("PLAYER  >>> TERMINATE"));
        }

        /**
         * MethodName: isAvailableActor
         * Purpose: Makes sure the actor that is looked far is an actual valid actor
         */
        private boolean isAvailableActor(String actorName){
            for(Draft.ActorData aD : d.getaDatabase()){
                if(aD != null) {
                    if (actorName.toLowerCase().contains(aD.getActorName().toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * MethodName: doDraft
         * Purpose: This runs through the draft and advances to the next person in the que
         */
        private void doDraft(String message) throws IOException {
            System.out.println(draftTurn && draftHappening);
            if(isAvailableActor(message)) {
                if (draftCounter == 4) {// Statement to end the draft
                    for (SockServer s : sockServer) {
                        if (s != null && s.alive) {
                            s.setDraftTurn(false);
                        }
                    }
                    draftHappened = true;// Lets the server know that the draft has happened already since it can only happen once
                    draftHappening = false;
                    messageToAll("\nDRAFT COMPLETED\n" +
                            "-------------------------\n");
                }
                if (draftTurn && draftHappening) {// If its their turn and t
                    for (Draft.ActorData aD : d.getaDatabase()) {
                        if (aD != null) {
                            if (message.toLowerCase().contains(aD.getActorName().toLowerCase()) && !aD.isClaimed()) {
                                aD.setClaimed(true); // Claims Actor So no other person can claim
                                aD.setClientNumber(myConID);
                                for (int i = 0; i < playerRoster.length; i++) {
                                    if (playerRoster[i] == null) {
                                        playerRoster[i] = aD;
                                        messageToAll("\nPlayer " + myConID + " CHOSE " + playerRoster[i].getActorName() + "\n");
                                        i = playerRoster.length;// stops initializing once it finds a place to put the new actor
                                    }
                                }
                            }
                        }
                    }
                    draftOrderPos++;
                    if (draftOrderPos < nClientsActive) {

                        System.out.println("Draft Order Player: " + draftOrder[draftOrderPos]);
                        sockServer[draftOrder[draftOrderPos]].setDraftTurn(true);// Advances the draft
                        sockServer[draftOrder[draftOrderPos]].sendRoster();
                        sockServer[draftOrder[draftOrderPos]].sendMessage("\nIT IS PLAYER " + (draftOrder[draftOrderPos] + 1) + "'s TURN TO DRAFT " + "\n");
                        messageToAll("\nIT IS PLAYER " + (draftOrder[draftOrderPos] + 1) + "'s TURN TO DRAFT " + "\n");
                    } else {

                        draftCounter++;
                        System.out.println(draftCounter);
                        draftOrderPos = 0;
                        System.out.println("Draft Order Player: " + draftOrder[draftOrderPos]);
                        sockServer[draftOrder[draftOrderPos]].setDraftTurn(true);// Advances the draft
                        sockServer[draftOrder[draftOrderPos]].sendRoster();

                        messageToAll("\nIT IS PLAYER " + (draftOrder[draftOrderPos] + 1) + "'s TURN TO DRAFT " + "\n");
                    }
                } else {
                    if (draftHappening) {
                        output.writeObject("\n** YOU ARE NOT ALLOWED TO DRAFT YET **\n");
                        output.flush();
                    }
                }
                setDraftTurn(false);// No longer their turn in the draft
            }
            else if(!isAvailableActor(message) && draftHappening){// Notifies the player if their choice is invalid
                sendMessage("\nNOT AN AVAILABLE ACTOR!\n");
            }
        }//End Draft selection

        private void sendMessage(String message) throws IOException {
            output.writeObject(message);
            output.flush();
        }

        /**
         * MethodName: runCommand
         * Purpose: Contains a switch statement of the commands within the game. Once one is called then
         * the command is executed will will have some response to either one or all of the players
         *
         * @param cmmd The command that the user input and would like to execute
         */
        private void runCommand(String cmmd){
            String message;
            switch (cmmd){
                case "$draft"://Begins the draft
                    try{
                        if(!draftHappened){
                            draftHappening = true;
                            startDraft();
                        }
                        else {
                            messageToAll("\nDRAFT ALREADY OCCURRED\n");
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$trade"://Starts a trade
                    trade();
                    break;
                case "$roster"://Prints the roster of the current player
                    try{
                        printRoster();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$addpoints"://Allows the player to add points to a character
                    try{
                        displayMessage("\nPOINT CHOICES DISPLAYED");
                        output.writeObject("\nPOINT CHOICES DISPLAYED");
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$infodraft":// Lets the player know what the $draft command does
                    try{
                        message = "\n$draft will begin the game. Use this when you would like to start the draft. \n       " +
                                "***MUST HAVE FRIENDS TO DRAFT***";
                        displayMessage(message);
                        output.writeObject(message);
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$infotrade":// Lets the player know what the $trade command does
                    try{
                        message = "\nTo trade [insert instructions]";
                        displayMessage(message);
                        output.writeObject(message);
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$inforoster":// Lets the player know what the $roster command does
                    try{
                        message = "\n$roster will display the actors you have drafted along with their points.";
                        displayMessage(message);
                        output.writeObject(message);
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$infoupdate":// Lets the player know what the $addpoints command does
                    try{
                        message = "\n$addpoints will allow you yo add points to an actor for the season based on their achievement.";
                        displayMessage(message);
                        output.writeObject(message);
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$totalroster"://Displays the entire roster with the updated info
                    try{
                        sendRoster();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$update":
                    updatePoints(cmmd);
                    break;
            }
        }

        /**
         * MethodName: updatePoints
         * Purpose: Simulate the drafted actors' performances.
         * @param cmmd The command that the user input and would like to execute
         */
        private void updatePoints(String cmmd)
        {
            String message;
            int week = 1;
            if (cmmd.contains("$update"))
            {
                if (draftHappened) {
                    for (int i = 0; i < 10; i++) {
                        try {
                            message = "----------------------------------------------------------\n" +
                                    "WEEK " + week + " of the Theater Fantasy Draft will now begin!\n";
                            //displayMessage(message);
                            messageToAll(message);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // for each connected client
                        for (SockServer player : sockServer) {
                                //for each client connected to the server
                                    // for each actor chosen by a client
                                    if(player != null && player.alive) {
                                        for (Draft.ActorData actor : player.playerRoster) {
                                            if (actor != null) {
                                                SecureRandom rand = new SecureRandom();
                                                int num = rand.nextInt(4);
                                                if (num == 0) {
                                                    try {
                                                        message = "During week " + week + ", " + actor.getActorName() + " got signed for a commercial!\n";
                                                        actor.setPoints(actor.getPoints() + 5);
                                                        //displayMessage(message);
                                                        messageToAll(message);

                                                        message = actor.getActorName() + " gained 5 points this week!\n";
                                                        //displayMessage(message);
                                                        messageToAll(message);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                if (num == 1) {
                                                    try {
                                                        message = "During week " + week + ", " + actor.getActorName() + " got signed for a voice acting role!\n";
                                                        actor.setPoints(actor.getPoints() + 10);
                                                        //displayMessage(message);
                                                        messageToAll(message);

                                                        message = actor.getActorName() + " gained 10 points this week!\n";
                                                        //displayMessage(message);
                                                        messageToAll(message);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                if (num == 2) {
                                                    try {
                                                        message = "During week " + week + ", " + actor.getActorName() + " got signed for a TV show!\n";
                                                        actor.setPoints(actor.getPoints() + 15);
                                                        //displayMessage(message);
                                                        messageToAll(message);

                                                        message = actor.getActorName() + " gained 15 points this week!\n";
                                                        //displayMessage(message);
                                                        messageToAll(message);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                if (num == 3) {
                                                    try {
                                                        message = "During week " + week + ", " + actor.getActorName() + " got signed for a lead role in a movie!\n";
                                                        actor.setPoints(actor.getPoints() + 20);
                                                        //displayMessage(message);
                                                        messageToAll(message);

                                                        message = actor.getActorName() + " gained 20 points this week!\n";
                                                        //displayMessage(message);
                                                        messageToAll(message);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                        week++;
                    }
                    }
                }
                else
                {
                    try {
                        message = "The draft has not concluded yet!";
                        //displayMessage(message);
                        messageToAll(message);
                    }
                    catch (IOException e) { e.printStackTrace(); }
                }
            }



        /**
         * MethodName: sendRoster
         * Purpose: Sends the information of the entire roster with the current data.
         *
         * @throws IOException
         */
        private void sendRoster() throws IOException {
            String message = "\nACTOR NAME, POINTS, OWNER\n" +
                    "---------------------------------------\n";
            for(Draft.ActorData aD : d.getaDatabase()){
                if(aD != null) {
                    message += aD.toString() + "\n";
                }
            }
            output.writeObject(message);
            output.flush();
        }
        // close streams and socket
        private void closeConnection() {
            displayMessage("\nTerminating connection " + myConID + "\n");
            displayMessage("\nNumber of connections = " + nClientsActive + "\n");
            alive = false;
            if (nClientsActive == 0) {
                setTextFieldEditable(false); // disable enterField
            }

            try {
                output.close(); // close output stream
                input.close(); // close input stream
                connection.close(); // close socket
            } // end try
            catch (IOException ioException) {
                ioException.printStackTrace();
            } // end catch
        } // end method closeConnection

        private void sendData(String message) {
            try // send object to client
            {
                output.writeObject("SERVER " + myConID + ": " + message);
                output.flush(); // flush output to client
                displayMessage("\nSERVER" + myConID + ">>> " + message);
            }
            catch (IOException ioException) {
                displayArea.append("\nError writing object");
            }
        }

        /**
         * Sends a trade request to another client.
         * Various checks to make sure trade is possible and sends a formal request to the
         * other client; which can be chosen to be accepted or not.
         */
        private void trade() {

            if (draftHappened) {

                sendData("Enter the player number you would like \n" +
                        " to trade with or type cancel to exit trade menu.");

                String messageFromTrader;

                // attributes to exit trade logic
                boolean exitTrade = false;
                boolean tradePreformedProperly = false;

                do {
                    try {
                        messageFromTrader = (String) input.readObject();
                    } catch (IOException | ClassNotFoundException e) {
                        System.out.println("Error when reading message");
                        messageFromTrader = null;
                    }

                    String[] tokens = messageFromTrader.split(" ");

                    if (tokens[1].toUpperCase().equals("CLOSE")) {
                        exitTrade = true;
                    }

                    // close was not entered
                    else {
                        try {
                            traderPlayerSelection = Integer.parseInt(tokens[1]);

                            // check to make sure client is valid
                            if (traderPlayerSelection > 0 && traderPlayerSelection <= nClientsActive && traderPlayerSelection != myConID) {
                                sendData("Roster shown bellow: \n");
                                sendRoster();

                                sendData("State the desired actor from other player or close to exit.");

                                try {
                                    messageFromTrader = (String) input.readObject();
                                } catch (IOException | ClassNotFoundException e) {
                                    System.out.println("Error when reading message");
                                    messageFromTrader = null;
                                }

                                String[] tokensTrade = messageFromTrader.split(" ");

                                System.out.println(messageFromTrader);

                                // check again if want to close
                                if (tokensTrade[1].toUpperCase().equals("CLOSE")) {
                                    exitTrade = true;
                                } else {
                                    actorToGet = tokensTrade[1] + " " + tokensTrade[2];

                                    sendData("State the desired actor you would trade in exchange for " + actorToGet);

                                    try {
                                        messageFromTrader = (String) input.readObject();

                                        tokensTrade = messageFromTrader.split(" ");
                                    } catch (IOException | ClassNotFoundException e) {
                                        System.out.println("Error when reading message");
                                        messageFromTrader = null;
                                    }

                                    if (tokensTrade[1].toUpperCase().equals("CLOSE")) {
                                        exitTrade = true;
                                    } else {
                                        myActorToGive = tokensTrade[1] + " " + tokensTrade[2];

                                        //check that actors selected are valid
                                        if (isActorInPossesion(actorToGet, traderPlayerSelection) && isActorInPossesion(myActorToGive, myConID)) {
                                            sockServer[traderPlayerSelection - 1].sendData("Player " + myConID + " would like to trade \n" +
                                                    myActorToGive + " in exchange for your actor " + actorToGet +
                                                    "\nType '$Trading yes " + myConID + " to accept, or nothing to decline");

                                            String response = null;

                                            displayMessage("Trade Sent. If trade is accepted, the changes to roster will be made automatically");

                                            tradePreformedProperly = true;
                                        } else {
                                            sendData("Trade could not be done, retry");
                                        }
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            sendData("Invalid. Enter Data in Correct Format");
                        } catch (IOException e) {
                            sendData("Could not send trade partner's roster.");
                        }
                    }
                } while (!exitTrade && !tradePreformedProperly); // client requesting to exit or trade info sent
            }
            else {
                displayMessage("Execute Draft First");
            }
        }

        /**
         * Validates that an actor is in possession of the client listed.
         * @param name  Name of the actor.
         * @param playerThatBelongsTo      Name of client to check if has possession of the actor
         * @return  True or false as to whether the client indeed has position of actor
         */
        public boolean isActorInPossesion(String name, int playerThatBelongsTo) {
            // iterate through client roster to check if actor name is listed in roster
            for (int i =0; i < sockServer[playerThatBelongsTo-1].playerRoster.length; i++) {
                if (sockServer[playerThatBelongsTo-1].playerRoster[i] != null && sockServer[playerThatBelongsTo-1].alive) {
                    if (sockServer[playerThatBelongsTo-1].playerRoster[i].getActorName().toLowerCase().equals(name.toLowerCase())) {
                        return true; // if actor is in client roster return true
                    }
                }
            }
            return false;
        }

        /**
         * Execute the proposed trade and change client roster to reflect actor changes.
         */
        public void executeTrade() {

            // placement of actor in this client roster
            int myPlace =-1;

            // placemetn of actor in trade partner's roster
            int tradePartnerPlace = -1;

            // determine posiiton of actor in respective rosters
            for (int i =0; i < playerRoster.length; i++) {
                if (playerRoster[i] != null)
                    if (playerRoster[i].getActorName().toLowerCase().equals(myActorToGive.toLowerCase())) {
                        myPlace = i;
                    }
            }
            for (int i =0; i < sockServer[traderPlayerSelection-1].playerRoster.length; i++) {
                if (sockServer[traderPlayerSelection-1].playerRoster[i] != null && sockServer[traderPlayerSelection-1].alive) {
                    if (sockServer[traderPlayerSelection-1].playerRoster[i].getActorName().toLowerCase().equals(myActorToGive.toLowerCase())) {
                        tradePartnerPlace = i;
                    }
                }
            }

            // exchange actor's in respective rosters
            Draft.ActorData[] tradingActor = new Draft.ActorData[1];
            tradingActor[0] = playerRoster[myPlace];
            playerRoster[myPlace] = sockServer[traderPlayerSelection].playerRoster[tradePartnerPlace];
            sockServer[traderPlayerSelection].playerRoster[tradePartnerPlace] = tradingActor[0];
        }
    }
}

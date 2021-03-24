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
import java.util.Iterator;
import java.util.Map;
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
    private String[] commmands = new String[]{"$draft", "$trade", "$roster", "$addpoints", "$totalroster",
                                            "$infodraft", "$infotrade","$inforoster", "$infoaddpoints"};

    public Server() {
        super("Server");

        sockServer = new SockServer[20]; // allocate array for up to 20 Players
        executor = Executors.newFixedThreadPool(20); //20 Players Max

        enterField = new JTextField(); // create enterField
        enterField.setEditable(false);
        enterField.addActionListener(
                new ActionListener() {
                    // send message to client
                    public void actionPerformed(ActionEvent event) {
                        // Just got text from Server GUI Textfield
                        // Now send this to each client -- broadcast mode
                        for (int i = 1; i <= counter; i++) {
                            if (sockServer[i].alive == true)
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

        private void waitForConnection() throws IOException {

            displayMessage("Waiting for Player " + myConID + "\n");
            connection = server.accept(); // allow server to accept connection
            displayMessage("Connection of Player " + myConID + " received from: " +
                    connection.getInetAddress().getHostName());
        }

        private void getStreams() throws IOException {
            // set up output stream for objects
            output = new ObjectOutputStream(connection.getOutputStream());
            output.flush(); // flush output buffer to send header information
            // set up input stream for objects
            input = new ObjectInputStream(connection.getInputStream());
            displayMessage("\nGot I/O streams\n");
        }


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

                    displayMessage("\n" + myConID + " " +  message);
                } // end try
                catch (ClassNotFoundException classNotFoundException) {
                    displayMessage("\nUnknown object type received");
                } // end catch

            } while (!message.equals("PLAYER  >>> TERMINATE"));
        }


        private boolean isAvailableActor(String actorName){
            /*Within the ActorData Class there is a boolean called isClaimed, just use that to determine whether the
            * actor has been drafted, if you want the specific person connected to that actor you want to use the int
            * clientNumber, which is also in the ActorData class which is the ID of the owner. */
            for(Draft.ActorData aD : d.getaDatabase()){
                if(aD != null) {
                    if (actorName.toLowerCase().contains(aD.getActorName().toLowerCase())) {
                        return true;
                    }
                }
            }
            return false;
        }


        private void doDraft(String message) throws IOException {
            System.out.println(draftTurn && draftHappening);
            if(isAvailableActor(message)) {
                if (draftCounter == 2) {//Drafts 3 people
                    for (SockServer s : sockServer) {
                        if (s != null && s.alive) {
                            s.setDraftTurn(false);
                        }
                    }
                    draftHappened = true;
                    draftHappening = false;
                    messageToAll("\nDRAFT COMPLETED\n" +
                            "-------------------------\n");
                }
                if (draftTurn && draftHappening) {
                    for (Draft.ActorData aD : d.getaDatabase()) {
                        if (aD != null) {
                            if (message.toLowerCase().contains(aD.getActorName().toLowerCase()) && !aD.isClaimed()) {
                                aD.setClaimed(true); // Claims Actor So no other person can claim
                                aD.setClientNumber(myConID);
                                for (int i = 0; i < playerRoster.length; i++) {
                                    if (playerRoster[i] == null) {
                                        playerRoster[i] = aD;
                                        messageToAll("\nPlayer " + myConID + " CHOSE " + playerRoster[i].getActorName() + "\n");
                                        i = playerRoster.length;
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
                setDraftTurn(false);
            }
            else if(!isAvailableActor(message) && draftHappening){
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
                    try{
                        displayMessage("\nTRADE STARTED");
                        output.writeObject("\nTRADE STARTED");
                        output.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "$roster"://Prints the roster of the current player
                    try{
                        displayMessage("\nDISPLAY ROSTER");
                        output.writeObject("\nDISPLAY ROSTER");
                        output.flush();
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
                        message = "\n$draft will begin the game. Use this when you would like to start the draft.";
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
                case "$infoaddpoints":// Lets the player know what the $addpoints command does
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
    }
}
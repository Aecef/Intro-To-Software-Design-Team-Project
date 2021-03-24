import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class Player extends JFrame {
    private JTextField enterField; // enters information from user
    private JTextArea displayArea; // display information to user
    private ObjectOutputStream output; // output stream to server
    private ObjectInputStream input; // input stream from server
    private String message = ""; // message from server
    private String chatServer; // host server for this application
    private Socket client; // socket to communicate with server
    private static int playerNum = 1;
    // initialize chatServer and set up GUI
    public Player(String host) {
        super("Player");
        playerNum++;
        chatServer = host;
        enterField = new JTextField();
        enterField.setEditable(false);
        enterField.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        sendData(event.getActionCommand());
                        enterField.setText("");
                    }
                }
        );

        add(enterField, BorderLayout.SOUTH);

        displayArea = new JTextArea();
        add(new JScrollPane(displayArea), BorderLayout.CENTER);

        setSize(500, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void runClient() {
        try
        {
            connectToServer();
            getStreams();
            processConnection();
        }
        catch (EOFException eofException) {
            displayMessage("\nPlayer terminated connection");
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
        finally {
            closeConnection(); // close connection
        }
    }

    private void connectToServer() throws IOException {
        displayMessage("Attempting connection\n");
        client = new Socket(InetAddress.getByName(chatServer), 23555);
        displayMessage("Welcome to THEATER FANTASY DRAFT\n" +
                "--------------------------------------------------------\n" +
                "Commands: \n" +
                "$draft    $trade    $roster    $addpoints    $totalroster\n" +
                "$infodraft    $infotrade    $inforoster    $infoaddpoints\n");
    }

    // get streams to send and receive data
    private void getStreams() throws IOException {
        // set up output stream for objects
        output = new ObjectOutputStream(client.getOutputStream());
        output.flush(); // flush output buffer to send header information

        // set up input stream for objects
        input = new ObjectInputStream(client.getInputStream());
        System.out.println("\nGot I/O Streams\n");
    }

    // process connection with server
    private void processConnection() throws IOException {
        setTextFieldEditable(true);
        do
        {
            try
            {
                message = (String) input.readObject(); // read new message

                String[] tokens = message.split(" ");

//                for (int i= 0; i < tokens.length; i++) {
//                    System.out.println(tokens[i]);
//                }

               try {
                   if (tokens[2].equals("N!TRadESequence1%$><J")) {
                       tradeSequence(Integer.parseInt(tokens[3]));
                   }
                   else {
                       throw new IndexOutOfBoundsException("No trade");
                   }
               }
               catch (IndexOutOfBoundsException e) {
                   displayMessage("\n" + message); // display message
               }
            }
            catch (ClassNotFoundException classNotFoundException) {
                displayMessage("\nUnknown object type received");
            }

        } while (!message.equals("SERVER  >>> TERMINATE"));
    }

    private void closeConnection() {
        displayMessage("\nClosing connection");
        setTextFieldEditable(false); // disable enterField

        try {
            output.close();
            input.close();
            client.close();
        }
        catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }
    private void sendData(String message) {
        try
        {
            output.writeObject("PLAYER: " + message);
            output.flush(); // flush data to output
            displayMessage("\nPLAYER: " + message);// To display sent message in current console
        }
        catch (IOException ioException) {
            displayArea.append("\nError writing object");
        }
    }

    // manipulates displayArea in the event-dispatch thread
    private void displayMessage(final String messageToDisplay) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() // updates displayArea
                    {
                        displayArea.append(messageToDisplay);
                    }
                }
        );
    }

    // manipulates enterField in the event-dispatch thread
    private void setTextFieldEditable(final boolean editable) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() { enterField.setEditable(editable);}
                }
        );
    }

    private void tradeSequence(int playerToTradeWith) throws IOException {
        displayMessage("Player " + playerToTradeWith + " wants to trade \nEnter anything to chat or $officalTrade to make offer \n " +
                "or close to exit");

        output.writeObject("$$%#@@1578985459!@44 " + playerToTradeWith);
        output.flush();

        do
        {
            try
            {
                message = (String) input.readObject(); // read new message

                displayMessage("\n" + message); // display message

            }
            catch (ClassNotFoundException | IOException classNotFoundException) {
                displayMessage("\nUnknown object type received");
            }

        } while (!message.equals("SERVER  >>> TERMINATE"));

    }

}

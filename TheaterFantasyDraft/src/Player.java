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
    private JTextField playerInput;
    private JTextArea displayArea;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private String message = "";
    private String chatServer;
    private Socket client;

    public Player(String host) {
        super("Player");
        chatServer = host;
        playerInput = new JTextField();
        playerInput.setEditable(false);
        playerInput.addActionListener(
                new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        sendData(event.getActionCommand());
                        playerInput.setText("");
                    }
                }
        );
        add(playerInput, BorderLayout.SOUTH);
        displayArea = new JTextArea();
        displayArea.setEditable(false);
        add(new JScrollPane(displayArea), BorderLayout.CENTER);
        setSize(500, 500);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * MethodName: runClient
     * Purpose: Establish a connection to a server and obtain the input ouput streams. This
     * will also help take in the information taken in by the server.
     */
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

    /**
     * MethodName: connectToServer
     * Purpose: Connects to a server and displays the available commands for the program
     */
    private void connectToServer() throws IOException {
        client = new Socket(InetAddress.getByName(chatServer), 23555);
        displayMessage("Welcome to THEATER FANTASY DRAFT\n" +
                "--------------------------------------------------------\n" +
                "Commands: \n" +
                "$draft    $trade    $roster    $update   $totalroster\n" +
                "$infodraft    $infotrade    $inforoster    $infoupdate\n");
    }

    /**
     * MethodName: getStreams
     * Purpose: Gets the streams and receives the data
     */
    private void getStreams() throws IOException {
        output = new ObjectOutputStream(client.getOutputStream());
        output.flush(); // flush output buffer to send header information
        input = new ObjectInputStream(client.getInputStream());
        System.out.println("\nGot I/O Streams\n");
    }

    /**
     * MethodName: processConnection
     * Purpose: Takes in the received data
     */
    private void processConnection() throws IOException {
        setTextFieldEditable(true);
        do
        {
            try
            {
                message = (String) input.readObject();

                displayMessage("\n" + message);
            }
            catch (ClassNotFoundException classNotFoundException) {
                displayMessage("\nUnknown object type received");
            }

        } while (!message.equals("SERVER  >>> TERMINATE"));
    }

    /**
     * MethodName: closeConnection
     * Purpose: Closes the connection with the server
     */
    private void closeConnection() {
        displayMessage("\nClosing connection");
        setTextFieldEditable(false);
        try {
            output.close();
            input.close();
            client.close();
        }
        catch (IOException ioException) {

            ioException.printStackTrace();
        }
    }

    private void displayMessage(final String messageToDisplay) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run()
                    {
                        displayArea.append(messageToDisplay);
                    }
                }
        );
    }
    private void sendData(String message) {
        try
        {
            output.writeObject("PLAYER: " + message);
            output.flush();
            displayMessage("\nPLAYER: " + message);
        }
        catch (IOException ioException) {
            displayArea.append("\nError writing object");
        }
    }

    /**
     * MethodName: setTextFieldEditable
     * Purpose: This helps
     */
    private void setTextFieldEditable(final boolean editable) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() { playerInput.setEditable(editable);}
                }
        );
    }

}

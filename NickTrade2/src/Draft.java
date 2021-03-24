import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

public class Draft
{
    File actors = new File("Actors.txt");
    private HashMap<String, ActorData> data = new HashMap<>(100);//Possibly dont need a HashMap
    private ActorData[] aDatabase = new ActorData[100];
    private ActorData[] claimedDraft = new ActorData[100];

    private String[] names = new String[100];
    private int[] values = new int[100];
    private int[] clientNumbers = new int[100];

    /**
     * ClassName: ActorData
     * Purpose: Will hold the information of the actor for the fantasy game
     */
    public static class ActorData{
        private int points, clientNumber; // Original value is their prev seasons points, clientNumber is the current owner
        private String actorName; //Actor's Name
        private boolean isClaimed = false; //Determines whether the player has been drafted

        ActorData(String actorName,int points, int clientNumber){
            this.points = points;
            this.clientNumber = clientNumber;
            this.actorName = actorName;
        }

        public void setClaimed(boolean claimed) {isClaimed = claimed;}
        public boolean isClaimed() { return isClaimed; }
        public String getActorName() { return actorName;}
        public int getClientNumber() {return clientNumber;}
        public int getPoints() {return points;}
        public void setClientNumber(int clientNumber) {this.clientNumber = clientNumber; }
        public void setPoints(int points) { this.points = points;}
        @Override
        public String toString() {
           return (getActorName() + ", " + getPoints() + ", " + getClientNumber());
        }
    }

    /**
     * MethodName: readFile
     * Purpose: Takes in the actor info from the file provided and stores them in an ActorData[]
     */
    public void readFile()
    {
        String[] information;
        int i = 0;
        try
        {
            Scanner sc = new Scanner(actors);
            Scanner word;
            while (sc.hasNextLine() && i < 100)
            {
                information = sc.nextLine().split(", ");
                aDatabase[i] = new ActorData(information[0], Integer.parseUnsignedInt(information[1].trim()), 0);
                data.put(information[0], aDatabase[i]);
                i++;
            }
            sc.close();
        }
        catch (FileNotFoundException e)
        {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }

    public ActorData[] getClaimedDraft() {return claimedDraft;}
    public ActorData[] getaDatabase() {
        return aDatabase;
    }
    public HashMap<String, ActorData> getData() {
        return data;
    }
    public int[] getValues() {
        return values;
    }
    public String[] getNames() {
        return names;
    }
    public int[] getClientNumber() {
        return clientNumbers;
    }

}
package set3;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    public static void main(String[] args)
    {
        Logger logger = Logger.getGlobal();

        try
        {
            if (args.length < 3)
            {
                throw new Exception("Not enough parameters (<file_name> <host_name> <port>)");
            }

            Client client = new Client(args[0], args[1], Integer.parseUnsignedInt(args[2]));

            logger.info("Successfully connected to " + args[1] + ':' + Integer.parseUnsignedInt(args[2])
                        + "\nFile transferring...\n");

            client.run();

            logger.info("File transferred successfully");
        }
        catch (Throwable err)
        {
            logger.log(Level.SEVERE, "Client error", err);
        }
    }
}
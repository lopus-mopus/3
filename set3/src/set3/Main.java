package set3;

import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main
{
    public static void main(String args[])
    {
        Logger logger = Logger.getGlobal();
        try
        {
            Server server = new Server("upload", logger);

            logger.info("Create server at " + server.getAddress() + '\n');

            server.run();

            logger.info("Server closed\n");
        }
        catch (Throwable err)
        {
            logger.log(Level.SEVERE, "Server unexpected error", err);
        }
    }
}

package set3;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Client
{
    public Client(String fileName, String hostName, int port) throws java.io.IOException
    {
        final File file = new File(fileName);

        inputDataStream = new FileInputStream(file);

        fileInfo = new FileInfo();
        fileInfo.fileName = file.getName();
        fileInfo.fileSize = file.length();

        clientSocket = new Socket(hostName, port);
    }

    public void run() throws java.io.IOException
    {
        final OutputStream outputDataStream = clientSocket.getOutputStream();
        final InputStream responseStream = clientSocket.getInputStream();

        {
            final ByteArrayOutputStream tempStream = new ByteArrayOutputStream(buffer.array().length);

            {
                final ObjectOutputStream objectTempStream = new ObjectOutputStream(tempStream);

                objectTempStream.writeObject(fileInfo);
                objectTempStream.flush();
            }

            buffer.put(tempStream.toByteArray(), 0, tempStream.size());
        }

        int read;
        byte readBuffer[] = new byte[10 * 1024 * 1024];

        while ((read = inputDataStream.read(readBuffer)) != -1)
        {
            for (int i = 0; i < read; i++)
            {
                if (!buffer.hasRemaining())
                {
                    outputDataStream.write(buffer.array());
                    buffer.clear();
                }

                buffer.put(readBuffer[i]);
            }
        }

        outputDataStream.write(buffer.array(), 0, buffer.position());

        if ((responseStream.read(readBuffer) < 1) || (readBuffer[0] == 0))
        {
            throw new java.io.IOException("File sending error - receive failed signal");
        }
    }

    private final InputStream inputDataStream;
    private final FileInfo fileInfo;
    private final Socket clientSocket;
    private final ByteBuffer buffer = ByteBuffer.allocate(Connections.packetSize);
}

package set3;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server
{
    public Server(String dstDirName, Logger logger) throws java.io.IOException
    {
        try
        {
        	//создаем папку еси ее нет
            Files.createDirectory(Paths.get(dstDirName));
        }
        catch (java.nio.file.FileAlreadyExistsException err)
        {
        }

        this.dstDir = new File(dstDirName);
        this.logger = logger;
//привязываем сервер к любому доступному порту
        serverSocket.bind(new InetSocketAddress(0));
        serverSocket.configureBlocking(false);
//регестрируем в селекторе
        serverSocket.register(selector, serverSocket.validOps());
//сохр адрес
        address = new InetSocketAddress(InetAddress.getLocalHost(),
                                        ((InetSocketAddress)serverSocket.getLocalAddress()).getPort());
    }


    public InetSocketAddress getAddress()
    {
        return address;
    }


    public void run() throws java.io.IOException, java.lang.ClassNotFoundException
    {
        while (true)
        {
        	//делаем селект с таймером 30с
            if (selector.select(30 * 1000) == 0)
            {
                for (SelectionKey key : selector.keys())
                {//если соб нет то завер
                    closeConnection(key);
                }
                break;
            }
            else
            {
            	//смотрим какие события произошли
                for (SelectionKey key : selector.selectedKeys())
                {
                    if (key.isAcceptable())
                    {//принимаем соеденения
                        accept(key);
                    }
                    else
                    {//читаем данные которые пришли
                        read(key);
                    }
                }

                selector.selectedKeys().clear();
            }
        }
    }



    private class ConnectionInfo
    {//смотрим пути к файлу, смотрим родительскую иректорию и если она не совподает с нужной 
    	//то соеденение закроется, а если норм то смотрит ли существует уже такой файл с таким именем в папке, если сущ то кидаем эксепшен
        //иначе открываем новый файл на запись
    	public final FileInfo fileInfo;
        public final OutputStream ostream;

        public ConnectionInfo(FileInfo fileInfo) throws java.io.IOException
        {
            this.fileInfo = fileInfo;

            final File file = new File(Server.this.dstDir, this.fileInfo.fileName);
            final Path path = Paths.get(file.getAbsolutePath());
            final Path parent = path.getParent();

            if (!parent.toString().equals(Server.this.dstDir.getAbsolutePath()))
            {
                throw new java.io.IOException("Client wants to upload file to " + parent.toString());
            }
            if (file.exists())
            {
                throw new java.io.IOException("File '" + file.getName() + "' already exists");
            }

            ostream = new FileOutputStream(file);
        }
    }

    private final ServerSocketChannel serverSocket = ServerSocketChannel.open();
    private final File dstDir;
    private final Selector selector = Selector.open();
    private final ByteBuffer buffer = ByteBuffer.allocate(Connections.packetSize);

    private final Logger logger;
    private final StringBuilder messageBuilder = new StringBuilder();

    private final InetSocketAddress address;

    private void accept(SelectionKey key) throws java.io.IOException
    {//делаем прием соединения, принимем соедениния, получаем новый сокет и ставим его не блок реж и рег в седекторе
        try
        {
            final SocketChannel channel = ((ServerSocketChannel)key.channel()).accept();

            if (channel != null)
            {
                if (logger != null)
                {
                    final InetSocketAddress clientAddress = (InetSocketAddress) channel.getRemoteAddress();

                    messageBuilder.append("Incoming connection from ")
                            .append(clientAddress.getAddress().getHostAddress())
                            .append(':')
                            .append(clientAddress.getPort())
                            .append("...\n");
                    logger.info(messageBuilder.toString());
                }

                channel.configureBlocking(false);
                channel.register(selector, channel.validOps());
            }
        }
        finally
        {
            messageBuilder.setLength(0);
        }
    }

    private void read(SelectionKey key) throws java.lang.ClassNotFoundException, java.io.IOException
    {//пытаемся читать из буфера, если данных нет то выходим из функции, из получиных данных сериализуем объект который представляет собой информацию о файле
        //после того как откоыли файл на запись пишем туда считанные данные с сокета, если получается то мы закрываем файл и соединение
    	try
        {
            if (((ReadableByteChannel)key.channel()).read(buffer) == 0)
            {
                return;
            }

            int writePosition = 0;

            if (key.attachment() == null)
            {
                final ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer.array(), 0, buffer.position());
                final ObjectInputStream objectStream = new ObjectInputStream(byteStream);

                final FileInfo fileInfo = (FileInfo)objectStream.readObject();
                final ConnectionInfo connectionInfo = new ConnectionInfo(fileInfo);

                key.attach(connectionInfo);

                writePosition = buffer.position() - byteStream.available();
            }

            final ConnectionInfo cInfo = (ConnectionInfo)key.attachment();
            final int length = buffer.position() - writePosition;

            cInfo.ostream.write(buffer.array(), writePosition, length);
            cInfo.fileInfo.fileSize -= length;

            if (cInfo.fileInfo.fileSize == 0)
            {
                cInfo.ostream.close();

                if (logger != null)
                {
                    messageBuilder.append("Finish receiving from : ")
                                  .append(((SocketChannel)key.channel()).getRemoteAddress().toString())
                                  .append("\nSave to file '")
                                  .append(cInfo.fileInfo.fileName)
                                  .append("'\nFinish connection.\n");
                    logger.info(messageBuilder.toString());
                }

                finishConnection(key);
            }
        }
        catch (java.io.IOException err)
        {
            if (logger != null)
            {
                messageBuilder.append("Receive data error")
                              .append("\nSender : ")
                              .append(((SocketChannel)key.channel()).getRemoteAddress().toString())
                              .append("\nClose connection.");

                logger.log(Level.SEVERE, messageBuilder.toString(), err);
            }

            closeConnection(key);
        }
        finally
        {
            buffer.clear();
            messageBuilder.setLength(0);
        }
    }

    private void finishConnection(SelectionKey key) throws java.io.IOException
    {//если ключ в силекторе валиден, смотрим является ли этот сокет с этим ключем доступен на запись
    	//если да то пишем в него содержимое буфера, которое  данный момент равно 1, потом закрываем сокеет и удалем ключ и удаляем инфо о файле ранее полученную
        buffer.clear();
        buffer.put((byte)1);

        if (key.isValid())
        {
            if (key.isWritable())
            {
                ((WritableByteChannel)key.channel()).write(buffer);
            }

            key.channel().close();
            key.cancel();
            key.attach(null);
        }
    }

    private void closeConnection(SelectionKey key) throws java.io.IOException
    {//экстренное закрытие то же самое что и выше, но не 1, а 0
        buffer.clear();
        buffer.put((byte)0);

        if (key.isValid())
        {
            if (key.isWritable())
            {
                ((WritableByteChannel)key.channel()).write(buffer);
            }

            key.channel().close();
            key.cancel();
            key.attach(null);
        }
    }
}
//0 и 1 разница в подтверждении что получил файл успешно


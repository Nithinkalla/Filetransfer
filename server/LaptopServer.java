import java.io.*;
import java.net.*;

public class LaptopServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9090);
        System.out.println("Server listening on port 9090...");

        while (true) {
            try (Socket socket = serverSocket.accept()) {
                System.out.println("Connected: " + socket.getInetAddress());

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

                String command = dis.readUTF();
                if ("SEND_FILES".equals(command)) {
                    dos.writeUTF("ACCEPT");
                    dos.flush();

                    int fileCount = dis.readInt();
                    File dir = new File("ReceivedFiles");
                    if (!dir.exists()) dir.mkdirs();

                    for (int i = 0; i < fileCount; i++) {
                        int fileNameLength = dis.readInt();
                        byte[] fileNameBytes = new byte[fileNameLength];
                        dis.readFully(fileNameBytes);
                        String fileName = new String(fileNameBytes);

                        long fileSize = dis.readLong();

                        File file = new File(dir, fileName);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            byte[] buffer = new byte[4096];
                            long remaining = fileSize;
                            int read;

                            while (remaining > 0 &&
                                   (read = dis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                                fos.write(buffer, 0, read);
                                remaining -= read;
                            }
                            fos.flush();
                        }

                        System.out.println("Received file: " + fileName + " (" + fileSize + " bytes)");
                    }
                } else {
                    dos.writeUTF("REJECT");
                    dos.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                try (
                        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Received from client: " + line);
                        String[] parts = line.split(" ");
                        String command = parts[0].toUpperCase();

                        try {
                            switch (command) {
                            case "CREATE":
                                fsManager.createFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' created.");
                                writer.println("<END>");
                                writer.flush();
                                break;

                            case "DELETE":
                                fsManager.deleteFile(parts[1]);
                                writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                                writer.println("<END>");
                                writer.flush();
                                break;
                            case "WRITE":
                                if (parts.length < 3) {
                                    writer.println("ERROR: Missing file data.");
                                    writer.println("<END>");
                                    writer.flush();
                                    break;
                                }
                                String fileData = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
                                fsManager.writeFile(parts[1], fileData.getBytes());
                                writer.println("SUCCESS: File '" + parts[1] + "' written.");
                                writer.println("<END>");
                                writer.flush();
                                break;

                            case "READ":
                                byte[] content = fsManager.readFile(parts[1]);
                                writer.println("CONTENT: " + new String(content));
                                writer.println("<END>");
                                writer.flush();
                                break;

                            case "LIST":
                                String[] files = fsManager.listFiles();
                                if (files.length == 0) {
                                    writer.println("No files found.");
                                } else {
                                    String joined = String.join(", ", files);
                                    writer.println("Files: " + joined);
                                }
                                writer.println("<END>");
                                writer.flush();
                                break;



                                case "QUIT":
                                writer.println("SUCCESS: Disconnecting.");
                                writer.println("<END>");
                                writer.flush();
                                return;
                            default:
                                writer.println("ERROR: Unknown command.");
                                writer.println("<END>");
                                writer.flush();
                                break;
                            }
                        } catch (Exception e) {
                            // Ensure client always receives an error response and the terminator
                            String msg = e.getMessage() == null ? "Internal server error" : e.getMessage();
                            writer.println("ERROR: " + msg);
                            writer.println("<END>");
                            writer.flush();
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}

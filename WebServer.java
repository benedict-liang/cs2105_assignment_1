import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

public class WebServer {
	
	public static void main (String args[]) throws Exception 
	{
		ServerSocket serverSocket = new ServerSocket(9092);
		while (true) {
			//accept connection
			Socket s = serverSocket.accept();

			//get input from client (browser)	
			InputStream is = s.getInputStream();
			InputStream is2 = s.getInputStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));

			//from server to client
			OutputStream os = s.getOutputStream();
			DataOutputStream output = new DataOutputStream(os);


			String inputString = br.readLine();
			String fields[] = inputString.split(" ");
			String filename = getFileName(fields);

			
			//get first line
			if (fields[0].equals("GET")) {

				//TODO: map to local disk, depending on root 

				//filter get query string
				String getQueryString = "";
				String filenameArray[] = filename.split("\\?");
				if (filenameArray.length > 1) {
					filename = filenameArray[0];
					getQueryString = filenameArray[1];
					getQueryString = getQueryString.replaceAll("&", " ");
				}

				String envRequestMethod = "REQUEST_METHOD=GET";
				String envQueryString = "QUERY_STRING=" + getQueryString;

				executeGETRequest(filename, output, getQueryString);
			}
			
			else if (fields[0].equals("POST")) {
				String postParameters = getPostParameters(br);
				postParameters = postParameters.replaceAll("&", " ");

				String command = "/usr/bin/perl " + filename + " " + postParameters;
				Process process = Runtime.getRuntime().exec(command);

				InputStream processIS = process.getInputStream();
				byte byteInput = (byte) processIS.read();
				if (byteInput !=-1) {
					ArrayList<Byte> contentTypeArr = new ArrayList<Byte>();
					ArrayList<Byte> arr = new ArrayList<Byte>();

					//get first line
					while (byteInput != '\n') {
						contentTypeArr.add(byteInput);
						byteInput = (byte) processIS.read();
					}

					String contentType = String.valueOf(contentTypeArr.toArray());

					while (byteInput != -1) {
						arr.add(byteInput);
						byteInput = (byte) processIS.read();
					}

					int size = arr.size();
					byte[] buffer = new byte[size];

					for (int i = 0; i < buffer.length; i++) {
						buffer[i] = arr.get(i);
					}

					//if p works
					output.writeBytes("HTTP/1.0 200 OK\r\n");
					//content type given by todo.pl
					output.writeBytes(contentType + "\r\n");
					output.write(buffer, 0, size);
				}

			}
			
			

			//close socket
			s.close();
		}
	}

	private static String getFileName(String[] fields) {
		String filename = fields[1];
		if (filename.startsWith("/")) {
			//remove initial slash
			filename = filename.substring(1);
		}

		return filename;
	}

	private static String getPostParameters(BufferedReader br) throws Exception {
		int contentLength = 0;
		String inLine = null;
		while (((inLine = br.readLine()) != null) && (!(inLine.equals("")))) {
			if (inLine.startsWith("Content-Length")) {
				String[] temp = inLine.split(" ");
				contentLength = Integer.parseInt(temp[1]);
			}
		}

		byte[] byteArr = new byte[contentLength];
		for (int i=0; i<contentLength; i++) {
			byteArr[i] = (byte)br.read();
		}

		return new String(byteArr);
	}

	private static void executeGETRequest(String filename, DataOutputStream output, String getQueryString) throws Exception {
		byte[] buffer = new byte[0];
		int size = 0;
		String contentType = "";
		if (filename.endsWith("css")) {

			File f = new File(filename);
			if (f.canRead()) {
				//send file back
				size = (int)f.length();
				buffer = new byte[size];
				FileInputStream fis = new FileInputStream(filename);
				fis.read(buffer);
				contentType = "Content-Type: text/css\r\n";
			}
		}
		else if (filename.endsWith("gif")) {

			File f = new File(filename);
			if (f.canRead()) {
				//send file back
				size = (int)f.length();
				buffer = new byte[size];
				FileInputStream fis = new FileInputStream(filename);
				fis.read(buffer);
				contentType = "Content-Type: image/gif\r\n";
			}
		}
		else if (filename.endsWith("pl")) {
			
			Process process = Runtime.getRuntime().exec("/usr/bin/perl " + filename + " " + getQueryString);
			InputStream processIS = process.getInputStream();
			byte byteInput = (byte) processIS.read();

			if (byteInput !=-1) {
				ArrayList<Byte> contentTypeArr = new ArrayList<Byte>();
				ArrayList<Byte> arr = new ArrayList<Byte>();
				
				//get first line
				while (byteInput != '\n') {
					contentTypeArr.add(byteInput);
					byteInput = (byte) processIS.read();
				}

				contentType = String.valueOf(contentTypeArr.toArray());

				while (byteInput != -1) {
					arr.add(byteInput);
					byteInput = (byte) processIS.read();
				}

				size = arr.size();
				buffer = new byte[size];

				for (int i = 0; i < buffer.length; i++) {
					buffer[i] = arr.get(i);
				}
				//if p works
				contentType += "\r\n";
			}
		}
		else
		{
			// File cannot be read.  Reply with 404 error.
			output.writeBytes("HTTP/1.0 404 Not Found\r\n");
			output.writeBytes("\r\n");
			output.writeBytes("No such page, please try again");
		}

		if (filename.endsWith("css") || filename.endsWith("pl") || filename.endsWith("gif")) {
			output.writeBytes("HTTP/1.0 200 OK\r\n");
			output.writeBytes(contentType);
			output.writeBytes("\r\n");
			output.write(buffer, 0, size);
		}
	}
}
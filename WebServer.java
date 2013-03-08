import java.util.*;
import java.io.*;
import java.net.*;
import java.lang.*;

public class WebServer {
	
	//TODO: TAKE IN PORT NUMBER AS COMMAND LINE ARGUMENT
	//TODO: ADD ENV VARIABLES
	
	public static void main (String args[]) throws Exception 
	{
		ServerSocket serverSocket = new ServerSocket(9092);
		while (true) {
			//accept connection
			Socket s = serverSocket.accept();

			//get input from client (browser)	
			InputStream is = s.getInputStream();
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
				String combinedEnvString = envRequestMethod + " " + envQueryString;

				executeGETRequest(filename, output, getQueryString);
			}
			
			else if (fields[0].equals("POST")) {
				String postParameters = getPostParameters(br);
				String[] parametersArray = new String[2];

				byte[] buffer = executePerlFile(filename, postParameters, parametersArray);
				
				//set content type and size of buffer
				String contentType = parametersArray[0];
				int size = Integer.parseInt(parametersArray[1]);

				//if p works
				output.writeBytes("HTTP/1.0 200 OK\r\n");
				output.writeBytes(contentType + "\r\n");
				output.write(buffer, 0, size);
			}

			//close socket
			s.close();
		}
	}

	/******************************************
	** Helper Methods
	******************************************/

	/**
	* Gets file name from the header field. Processes it by removing the initial slash.
	* 
	* return file name (without initial slash)
	**/
	private static String getFileName(String[] fields) {
		String filename = fields[1];
		if (filename.startsWith("/")) {
			//remove initial slash
			filename = filename.substring(1);
		}

		return filename;
	}

	/**
	* Replaces whitespace and '&' with characters that perl can process
	* 
	* return escaped string
	**/
	private static String replaceSpecialCharacters(String string) {
		string = string.replaceAll(" ", "+");
		string = string.replaceAll("&", "%26");
		return string;
	}

	/**
	* Runs the perl file.
	* Stores content type and buffer size in the parametersArray via referencing.
	*
	* return content buffer
	**/
	private static byte[] executePerlFile(String filename, String parameters, String[] parametersArray) throws IOException {
		byte[] buffer = new byte[0];
		String command = "/usr/bin/perl " + filename + " " + parameters;
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
			buffer = new byte[size];

			for (int i = 0; i < buffer.length; i++) {
				buffer[i] = arr.get(i);
			}

			parametersArray[0] = contentType;
			parametersArray[1] = String.valueOf(size);
 		}

		return buffer;
	}

	/******************************************
	** GET Methods
	******************************************/

	/**
	* Executes GET request for file types: .css, .gif, and .pl.
	*
	**/
	private static void executeGETRequest(String filename, DataOutputStream output, String queryStringGET) throws Exception {
		boolean isFileCSS = filename.endsWith("css");
		boolean isFileGif = filename.endsWith("gif");
		boolean isFilePl = filename.endsWith("pl");

		byte[] buffer = new byte[0];
		int size = 0;
		String contentType = "";
		
		if (isFilePl) {
			String[] parametersArray = new String[2];
			buffer = executePerlFile(filename, queryStringGET, parametersArray);

			//set content type and size of buffer
			contentType = parametersArray[0];
			size = Integer.parseInt(parametersArray[1]);
		}
		else if (isFileCSS || isFileGif) {

			File file = new File(filename);
			if (file.canRead()) {
				//send file back
				size = (int)file.length();
				buffer = new byte[size];
				FileInputStream fileInputStream = new FileInputStream(filename);
				//read file input stream to buffer array
				fileInputStream.read(buffer);

				//set content type
				if (isFileCSS) {
					contentType = "Content-Type: text/css\r\n";	
				}
				else {
					contentType = "Content-Type: image/gif\r\n";
				}
			}
		}
		else
		{
			// File cannot be read.  Reply with 404 error.
			output.writeBytes("HTTP/1.0 404 Not Found\r\n");
			output.writeBytes("\r\n");
			output.writeBytes("No such page, please try again");
		}

		if (isFileCSS || isFileGif || isFilePl) {
			output.writeBytes("HTTP/1.0 200 OK\r\n");
			output.writeBytes(contentType + "\r\n");
			output.write(buffer, 0, size);
		}
	}

	/******************************************
	** POST Methods
	******************************************/

	/**
	* Represent parameters from both types of POST requests with a key-value pair (key=value).
	*
	* return POST parameters
	**/
	private static String getPostParameters(BufferedReader br) throws Exception {
		int contentLength = 0;
		String contentType = "";
		String inLine = null;
		while (((inLine = br.readLine()) != null) && (!(inLine.equals("")))) {
			if (inLine.startsWith("Content-Type")) {
				String[] temp = inLine.split(" ");
				contentType = temp[1];
			}
			if (inLine.startsWith("Content-Length")) {
				String[] temp = inLine.split(" ");
				contentLength = Integer.parseInt(temp[1]);
			}
		}
		
		return filterPostParametersFromBufferedReader(br, contentType, contentLength);
	}

	/**
	* Gets POST parameters after filtering by content-types.
	* Content-types that are excepted include: "application/x-www-form-urlencoded" and
	* "multipart/form-data". Since the representations for both types are different in their
	* respective headers, the filtering process will be different.
	*
	* return POST parameters
	**/
	private static String filterPostParametersFromBufferedReader(BufferedReader br, 
		String contentType, int contentLength) throws Exception {
		byte[] byteArr = new byte[contentLength];
		for (int i=0; i<contentLength; i++) {
			byteArr[i] = (byte)br.read();
		}
		String result = "";

		if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
			result = new String(byteArr);
		}
		else if (contentType.equalsIgnoreCase("multipart/form-data;")) {
			String header = new String(byteArr);
			result = processMultipartData(header);
		}
		return result;
	}

	/**
	* Processes the POST query of Content-Type: "multipart/form-data".
	* The header is processed using knowledge of the basic structure of this type of 
	* POST query. Filtering is done using String positions (which should be relatively fast
	* performance wise as compared to REGEX).
	*
	* return "multipart/form-data" parameters
	**/
	private static String processMultipartData(String header) {
		String result = "";

		//start and end positions of substring
		int posStart = 0, posEnd = 0;
		StringBuffer sb = new StringBuffer();

		while (true) {
			posStart = header.indexOf("Content-Disposition:", posEnd + 1);
			posStart = header.indexOf("\"", posStart);
			if (posStart < posEnd) {
				break;
			}
			posEnd = header.indexOf("\"", posStart + 1);
			
			//parameter
			String substring = header.substring(posStart + 1, posEnd);

			sb = sb.append(substring + "=");

			posStart = header.indexOf("\r\n\r\n", posEnd + 1);
			posEnd = header.indexOf("------WebKitFormBoundary", posStart + 1);
			//parameter value
			substring = header.substring(posStart + 4, posEnd - 2);
			substring = replaceSpecialCharacters(substring);

			sb = sb.append(substring + " ");
		}

		result = sb.toString();
		return result;
	}
}

import java.net.*;
import java.io.*;
import java.util.*;

public class ZeptoHttpd implements Runnable{

	static final String CRLF="\r\n";
	static final int maxChannel=10;
	static ZeptoHttpd ChannelList[]=new ZeptoHttpd[maxChannel];
	static String userDir=null;
	static Hashtable<String,String> contentTypeTable=new Hashtable<String,String>();
	static Hashtable<String,String> filterTable=new Hashtable<String,String>();
	static Hashtable<String,String> settings=new Hashtable<String,String>();

	/**
	 * Main
	 */
	static public void main(String arg[]){
		String params = null;
		ServerSocket s_socket = null;

		if(arg.length > 0) params = arg[0];
		
		init(params);

		int port=0;
		if(arg.length>=1){port=Integer.parseInt(arg[0]);}
		try{
			s_socket = new ServerSocket(port);
			
			System.out.println(new Date().toString() + "START http://localhost:"+s_socket.getLocalPort());
			try{
				while(true){
					Socket socket = s_socket.accept();
					setChannel(socket);
				}
			} catch(IOException e){e.printStackTrace();}
			s_socket.close();
		 	System.out.println(new Date().toString() + "End Web_Server");
		}catch(IOException e){e.printStackTrace();}
	}

	/**
	 * Initialize
	 */
	static public void init(String params){
		userDir = System.getProperties().getProperty("user.dir");
		settings = loadSettingToHash("settings.txt");
		
		contentTypeTable = loadSettingToHash("mime.txt");
		if(contentTypeTable.isEmpty()){
			contentTypeTable.put("html",	 "text/html");
			contentTypeTable.put("htm",		 "text/html");
			contentTypeTable.put("css",		 "text/css");
			contentTypeTable.put("gif",		 "image/gif");
			contentTypeTable.put("jpg",		 "image/jpeg");
			contentTypeTable.put("jpeg",	 "image/jpeg");
			contentTypeTable.put("png",		 "image/png");
			contentTypeTable.put("svg",		 "image/svg+xml");
			contentTypeTable.put("mov",		 "video/quicktime");
			contentTypeTable.put("qt",		 "video/quicktime");
			contentTypeTable.put("class",	 "application/octet-stream");
			contentTypeTable.put("js",		 "application/x-javascript");
		}

		filterTable = loadSettingToHash("filter.txt");
		
	}

	static public Hashtable<String,String> loadSettingToHash(String name){
		Hashtable<String,String> res = new Hashtable<String,String>();
		String set_file = userDir+File.separatorChar+name;
		if(!(new File(set_file)).exists()) return res;
		
		BufferedReader br = null;
		try {

			br = new BufferedReader(new InputStreamReader(new FileInputStream(set_file),settings.getOrDefault("encoding","UTF-8")));

			String line;
			while ((line = br.readLine()) != null) {
				if(line.startsWith("#")) continue;
				if(!line.contains("==>")) continue;
				String[] a_line = line.split("==>");
				res.put(a_line[0],a_line[1]);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return res;
	}

	public static String readAll(String path) throws IOException {
		StringBuilder builder = new StringBuilder();
	
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path),settings.getOrDefault("encoding","UTF-8")))) {
			String string = reader.readLine();
			while (string != null){
				builder.append(string + System.getProperty("line.separator"));
				string = reader.readLine();
			}
		}
	
		return builder.toString();
	}

	/**
	 * Channel Management
	 */
	static public void setChannel(Socket socket){
		ZeptoHttpd obj=null;
		for(int i=0;i<maxChannel;i++){
		    if(ChannelList[i]==null){
		    	ChannelList[i]=new ZeptoHttpd();
		    }
			if(ChannelList[i].active==false){
				obj=ChannelList[i];
				break;
			}
		}
		if(obj!=null){
	    	obj.startChannel(socket);
		}
	}

	static public String getContentType(String name) {
	    int pos=name.lastIndexOf(".");
	    String type=null;
	    if(pos>-1){
	    	type=name.substring(pos+1);
	    	type=type.toLowerCase();
	     	type = (String) contentTypeTable.get(type);
	     	}
		if(type==null) type="text/plain";
		return type;
	}

	boolean active=false;
	Socket socket=null;
	BufferedReader input=null;
	PrintWriter output=null;
	OutputStream raw_output=null;
	
	public void startChannel(Socket socket){
	    active=true;
		this.socket = socket;
		try{
			input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"JISAutoDetect"));
			raw_output=socket.getOutputStream();
			output=new PrintWriter(new OutputStreamWriter(raw_output,settings.getOrDefault("encoding","UTF-8")));
		} catch(IOException e){e.printStackTrace();}
		Thread thread=new Thread(this);
		thread.start();
	}
	
	void send_Header(PrintWriter printWriter, String firstLine, long contentLength,String contentType){
		if(printWriter==null) return;
		printWriter.print(firstLine);printWriter.print(CRLF);
		printWriter.print("Connection: close");printWriter.print(CRLF);
		if(contentLength>0){
			printWriter.print("Content-Length: ");printWriter.print(contentLength);printWriter.print(CRLF);
		}
		if(contentType!=null) {
			if(contentType.equals("text/html")) contentType = "text/html; charset="+settings.getOrDefault("encoding","UTF-8");
			printWriter.print("Content-Type: ");printWriter.print(contentType);printWriter.print(CRLF);
		}

		printWriter.print(CRLF);
		printWriter.flush();
		return;
	}
	
	void send_Body(OutputStream outputStream,File file){
		try{
			FileInputStream fileInputStream=new FileInputStream(file);
			
			byte line[]=new byte[500];
			int i;
			while (( i = fileInputStream.read(line,0,500)) != -1 ) {
				outputStream.write(line,0,i);
			}	
			outputStream.flush();
			fileInputStream.close();
		} catch(IOException e){
			System.out.println("NotFound: " + file.getName());
		}
	}

	void send_Body_text(OutputStream outputStream,String text){
		try{
			InputStream is = new ByteArrayInputStream( text.getBytes(settings.getOrDefault("encoding","UTF-8")) );
			byte line[]=new byte[500];
			int i;
			while (( i = is.read(line,0,500)) != -1 ) {
				outputStream.write(line,0,i);
			}	
			outputStream.flush();
			is.close();
		} catch(IOException e){
			System.out.println("NotFound: ");
		}
	}

	public void run(){
		String header = "";
		try{
			header =input.readLine();
			input.readLine();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch(IOException e){
			e.printStackTrace();
		}
		
		try{
			String  request = null;
			StringTokenizer stringTokenizer = null;
			if(header != null){
				stringTokenizer = new StringTokenizer(header);
				request = stringTokenizer.nextToken();
			}

			if (request != null && (request.equals("GET") || request.equals("HEAD"))){
				String pathAndName= stringTokenizer.nextToken();
				if(pathAndName.endsWith("/")) pathAndName=pathAndName+"index.html";
				File file =new File(userDir + pathAndName.replace('/', File.separatorChar).replaceAll("\\?.*",""));

				if(file.length() > 0){
					
					if(request.equals("GET")){
						if(getContentType(file.getName()).equals("text/html")
						|| getContentType(file.getName()).equals("text/css")
						|| getContentType(file.getName()).equals("application/x-javascript")
						){
							String out_string = readAll(file.getPath());
							
							Enumeration keys = filterTable.keys();

							while(keys.hasMoreElements()){
								String key = (String) keys.nextElement();

								if(key.endsWith("/g")){
									out_string = out_string.replaceAll(key.replaceAll("/g$",""),filterTable.get(key));
								}else{
									out_string = out_string.replace(key,filterTable.get(key));
								}
								
							}

							send_Header(output,"HTTP/1.0 200 OK",out_string.getBytes(settings.getOrDefault("encoding","UTF-8")).length,getContentType(file.getName()));
							send_Body_text(raw_output,out_string);
						}else{
							send_Header(output,"HTTP/1.0 200 OK",file.length(),getContentType(file.getName()));
							send_Body(raw_output,file);
						}
					}else{
						send_Header(output,"HTTP/1.0 200 OK",file.length(),getContentType(file.getName()));
					}
				}else{
					String body="<HTML><HEAD></HEAD><BODY><H1>Error 404 File not found.</H1><ul>";

					File[] indexlist = file.getParentFile().listFiles();

					body+="<li><a href='../'>../</a></li>";

					for(int i=0; i<indexlist.length; i++){
						if(indexlist[i].isDirectory()){
							body += "<li><a href='"+indexlist[i].getName()+"/'>"+indexlist[i].getName()+"/</a></li>";
						}else if(indexlist[i].getName().endsWith(".html") || indexlist[i].getName().endsWith(".html")){
							body += "<li><a href='"+indexlist[i].getName()+"'>"+indexlist[i].getName()+"</a></li>";
						}
					}

					body += "</ul></BODY></HTML>";

					send_Header(output,"HTTP/1.0 404 NotFound",body.length(),"text/html");
					output.print(body);
					output.flush();
					System.out.println("NotFound: " + header);
				}
			}else{
				final String body="<HTML><HEAD></HEAD><BODY><H1>Unsupported Command</H1></BODY></HTML>";
				send_Header(output,"HTTP/1.0 200 OK",body.length(),"text/html");
				output.print(body);
				output.flush();
			}
		} catch(Exception e){
			System.out.println("NotFound: "+header);
		}

		try {
			input.close();input=null;
			output.close();output=null;
			socket.close();socket=null;
		} catch (IOException e) {e.printStackTrace();}
		System.out.flush();
		active=false;
	}
} 

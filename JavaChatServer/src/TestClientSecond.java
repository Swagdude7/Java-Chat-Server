import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class TestClientSecond {
	
	public static void main(String[] args) {
		DirectoryClientConnector testClient1 = new DirectoryClientConnector("localhost", 11115, "testClient1");
		try {
			int destID = 0;
		testClient1.logon();
		testClient1.ping();
		ArrayList<String> list1 = testClient1.getList();
		for(String s : list1) {
			String[] stringArray = s.split(":");
			if(Integer.parseInt(stringArray[3]) == 11125) {
				destID = Integer.parseInt(stringArray[0]);
			}
		}
		testClient1.setChatDest(destID);
		testClient1.chat();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		
		
	}

}

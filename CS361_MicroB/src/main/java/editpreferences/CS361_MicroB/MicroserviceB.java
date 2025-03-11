package editpreferences.CS361_MicroB;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JScrollPane;

import java.io.FileReader;
import java.io.FileWriter;

import org.zeromq.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;

public class MicroserviceB extends JFrame {

	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField portInput;
	private JButton submitBttn;
	
	public JScrollPane scrollPane;
	public JLabel logLab;
	
	public Log log;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MicroserviceB frame = new MicroserviceB();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public MicroserviceB() {
		log = new Log(this);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 650, 700);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		// Set up labels, port input, and submit button.
		JLabel titleLab = new JLabel("Edit Preferences");
		titleLab.setHorizontalAlignment(SwingConstants.CENTER);
		titleLab.setBounds(158, 60, 319, 51);
		titleLab.setFont(new Font("Serif", Font.BOLD, 35));
		contentPane.add(titleLab);
		
		JLabel portLab = new JLabel("Port:");
		portLab.setHorizontalAlignment(SwingConstants.CENTER);
		portLab.setBounds(44, 177, 91, 43);
		portLab.setFont(new Font("Serif", Font.BOLD, 20));
		contentPane.add(portLab);
		
		portInput = new JTextField();
		portInput.setBounds(168, 177, 234, 43);
		portInput.setFont(new Font("Serif", Font.BOLD, 20));
		portInput.setColumns(10);
		contentPane.add(portInput);
		
		logLab = new JLabel("Listening on Port: ");
		logLab.setVerticalAlignment(SwingConstants.TOP);
		logLab.setBounds(90, 355, 456, 255);
		logLab.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		logLab.setFont(new Font("Serif", Font.PLAIN, 15));
		
		scrollPane = new JScrollPane(logLab);
		scrollPane.setBounds(90, 355, 456, 255);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		contentPane.add(scrollPane);
		
		submitBttn = new JButton("Submit");
		submitBttn.setBounds(444, 266, 91, 43);
		submitBttn.setFont(new Font("Serif", Font.BOLD, 15));
		submitBttn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent ae) {
				new EditPreferences(portInput.getText(), log);
			}
		});
		contentPane.add(submitBttn);
	}
}

class EditPreferences extends MicroserviceB{
	private static final long serialVersionUID = 1L;
	int portNum;
	JSONObject reqObj;
	JSONObject repObj;
	Log log;
	
	@SuppressWarnings("unchecked")
	public EditPreferences(String portStr, Log log) {
		byte[] req;
		ZMQ.Socket socket;
		this.log = log;
		repObj = new JSONObject();
		
		while (true) {
			// Get port number
			try {
				portNum = Integer.parseInt(portStr);
			} catch (Exception e) {
				log.addLine("Error with port number: " + e);
				e.printStackTrace();
				break;
			}
			
			// Listen
			try (ZContext context = new ZContext()) {
				// Create socket
				socket = context.createSocket(SocketType.REP);
				socket.bind("tcp://*:" + portNum);
				log.addLine("Connected to port: " + portNum);

				// Wait for request
				req = socket.recv();
			
				// Extract request
				try {
					JSONParser parser = (JSONParser) new JSONParser();
					reqObj = (JSONObject) parser.parse(new String(req, ZMQ.CHARSET));
				} catch (Exception e) {
					log.addLine("Error in extracting request");
					e.printStackTrace();
				}
				String reqStr = (String) reqObj.get("req");
				log.addLine("Recieved request: " + reqStr);
				
				// Fulfill request
				int result = 0;
				switch (reqStr) {
					case "getall":
						result = returnAll();
						break;
					case "update":
						result = update();
						break;
				}
				
				// Close connection if exit
				if (reqStr.equals("exit")) {
					break;
				}
				
				// Send reply
				repObj.put("code", result);
				byte[] reply = repObj.toString().getBytes(ZMQ.CHARSET);
				socket.send(reply, 0);
				log.addLine("Successfully sent reply");
	
			} catch (Exception e) {
				log.addLine("Error: " + e);
				e.printStackTrace();
				break;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private int returnAll() {
		// Get all preferences
		String path = (String) reqObj.get("path");
		JSONArray arr;
		try {
			JSONParser parser = (JSONParser) new JSONParser();
			arr = (JSONArray) parser.parse(new FileReader(path));
		} catch (Exception e) {
			log.addLine("Error returning all: " + e);
			e.printStackTrace();
			return 0;
		}
		repObj.put("result", arr);
		log.addLine("Successfully returned all Preferences");
		return 1;
	}
	
	private int update() {
		// Get all preferences
		String path = (String) reqObj.get("path");
		JSONArray updated = (JSONArray) reqObj.get("updatedvals");
		
		// Write all values to JSON file.
		try (FileWriter fw = new FileWriter(path)) {
			fw.write(updated.toString());
		} catch (Exception e) {
			log.addLine("Error updating: " + e);
			e.printStackTrace();
			return 0;
		}
		log.addLine("Successfully updated values in Preferences");
		return 1;
	}
}

//Keep a log
class Log {
	private String logStr;
	private MicroserviceB mainUI;
	
	public Log(MicroserviceB mainUI) {
		logStr = "";
		this.mainUI = mainUI;
	}
	
	void addLine(String line) {
		logStr += "<br/>" + line;
		mainUI.logLab.setText("<html>" + logStr + "</html");
		mainUI.scrollPane.getVerticalScrollBar().setValue(mainUI.scrollPane.getVerticalScrollBar().getMaximum());
		mainUI.revalidate();
		mainUI.repaint();
	}
}

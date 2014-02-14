package com.subgraph.sgmail.ui.dialogs;

import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;

import com.subgraph.sgmail.servers.ServerInformation;
import com.subgraph.sgmail.servers.ServerInformation.SocketType;

public class AccountTestLoginTask implements IRunnableWithProgress {
	private final static Logger logger = Logger.getLogger(AccountTestLoginTask.class.getName());
	
	private final Properties sessionProperties;
	//private final NewAccountDialog dialog;
	private final ServerInformation server;
	private final String login;
	private final String password;
	
	private boolean isSuccess;
	private String errorMessage = "";
	
	AccountTestLoginTask(NewAccountDialog dialog, ServerInformation server, String login, String password) {
		//this.dialog = dialog;
		this.server = server;
		this.login = login;
		this.password = password;
		this.sessionProperties = new Properties();
	}

	public boolean isSuccess() {
		return isSuccess;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	@Override
	public void run(IProgressMonitor monitor) {
		monitor.beginTask("Test login credentials", IProgressMonitor.UNKNOWN);
		
		final String protocol = getProtocolName();
		setupSessionProperties(protocol);
		Session session = Session.getInstance(sessionProperties);
		
		Store store;
		try {
			store = session.getStore(protocol);
			store.connect(login, password);
			store.close();
			isSuccess = true;
			//dialog.accountVerificationSucceeded();
		} catch (AuthenticationFailedException e) {
			isSuccess = false;
			errorMessage = "Login failed";
			//dialog.accountVerificationFailed("Login failed", true);
		} catch (NoSuchProviderException e) {
			isSuccess = false;
			errorMessage = e.getMessage();
			//dialog.accountVerificationFailed(e.getMessage(), false);
			logger.warning("Could not test login credentials: "+ e.getMessage());
		} catch (MessagingException e) {
			isSuccess = false;
			errorMessage = e.getMessage();
			//dialog.accountVerificationFailed(e.getMessage(), false);
			logger.warning("Error testing login credentials: "+ e.getMessage());
		}
	}
	
	private void setupSessionProperties(String protocol) {
		
		set("mail.store.protocol", protocol);
		set("mail."+ protocol +".host", server.getHostname());
		set("mail."+ protocol +".port", Integer.toString(server.getPort()));
		
		if(server.getSocketType() == SocketType.STARTTLS) {
			set("mail."+ protocol +".starttls.enable", "true");
			set("mail."+ protocol +".starttls.required", "true");
		}
	}

	private void set(String name, String value) {
		sessionProperties.setProperty(name, value);
	}
	
	
	private boolean isSecureSocket() {
		return server.getSocketType() == SocketType.SSL;
	}
	
	private String getProtocolName() {
		final boolean ss = isSecureSocket();
		switch(server.getProtocol()) {
		case IMAP:
			return (ss) ? ("imaps") : ("imap");

		case POP3:
			return (ss) ? ("pop3s") : ("pop3");
			
		case SMTP:
		case UNKNOWN:
		default:
			throw new IllegalArgumentException("Incoming server protocol has invalid value "+ server.getProtocol());
		}
	}


}
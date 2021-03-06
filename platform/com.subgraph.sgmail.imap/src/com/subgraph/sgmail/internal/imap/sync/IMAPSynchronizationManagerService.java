package com.subgraph.sgmail.internal.imap.sync;

import java.util.HashMap;
import java.util.Map;

import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.subgraph.sgmail.IEventBus;
import com.subgraph.sgmail.JavamailUtils;
import com.subgraph.sgmail.database.Model;
import com.subgraph.sgmail.database.Preferences;
import com.subgraph.sgmail.events.AccountAddedEvent;
import com.subgraph.sgmail.events.DatabaseOpenedEvent;
import com.subgraph.sgmail.imap.IMAPAccount;
import com.subgraph.sgmail.imap.IMAPAccountList;
import com.subgraph.sgmail.imap.IMAPSynchronizationManager;
import com.subgraph.sgmail.messages.MessageFactory;
import com.subgraph.sgmail.nyms.NymsAgent;
import com.subgraph.sgmail.search.MessageSearchIndex;

public class IMAPSynchronizationManagerService implements IMAPSynchronizationManager {

  private final Map<IMAPAccount, AccountSynchronizer> synchronizers = new HashMap<>();
  private MessageSearchIndex messageSearchIndex;
  private IEventBus eventBus;
  private ListeningExecutorService executorService;
  private Model model;
  private JavamailUtils javamailUtils;
  private NymsAgent nymsAgent;
  private MessageFactory messageFactory;

  private boolean isRunning;

  public void activate() {
    eventBus.register(this);
  }

  public void deactivate() {
    eventBus.unregister(this);
  }

  public void setSearchIndex(MessageSearchIndex searchIndex) {
    this.messageSearchIndex = searchIndex;
  }

  public void setExecutor(ListeningExecutorService executorService) {
    this.executorService = executorService;
  }

  public void setModel(Model model) {
    this.model = model;
  }

  public void setEventBus(IEventBus eventBus) {
    this.eventBus = eventBus;
  }

  public void setJavamailUtils(JavamailUtils javamailUtils) {
    this.javamailUtils = javamailUtils;
  }

  public void setNymsAgent(NymsAgent nymsAgent) {
    this.nymsAgent = nymsAgent;
  }

  public void setMessageFactory(MessageFactory messageFactory) {
    this.messageFactory = messageFactory;
  }

  @Subscribe
  public void onDatabaseOpened(DatabaseOpenedEvent event) {
    refreshSyncronizers();
  }

  private void refreshSyncronizers() {
    final IMAPAccountList imapAccountList = model.getDatabase().getSingleton(IMAPAccountList.class);
    if(imapAccountList == null) {
      return;
    }
    synchronized (synchronizers) {
      for (IMAPAccount account : imapAccountList.getAccounts()) {
        if (!synchronizers.containsKey(account)) {
          synchronizers.put(account, createSynchronizer(account));
        }
      }
    }
  }

  private AccountSynchronizer createSynchronizer(IMAPAccount account) {
    final Preferences prefs = model.getRootPreferences();
    return new AccountSynchronizer(prefs, executorService, messageFactory, model, javamailUtils, nymsAgent,
        messageSearchIndex, account);
  }

  /*
  	public void updateFlag(IMAPAccount account, StoredIMAPMessage message, Flag flag, boolean isSet) {
  		synchronized(synchronizers) {
  			if(synchronizers.containsKey(account)) {
  				synchronizers.get(account).updateFlags(message, flag, isSet);
  			} else {
  				throw new IllegalStateException("No entry found in synchronization manager for account");
  			}
  		}
  	}
  	*/

  @Override
  public synchronized void start() {
    if (isRunning) {
      return;
    }
    refreshSyncronizers();

    synchronized (synchronizers) {
      for (IMAPAccount account : synchronizers.keySet()) {
        if (account.isAutomaticSyncEnabled()) {
          synchronizers.get(account).start();
        }
      }
    }
    isRunning = true;
  }

  @Override
  public synchronized void stop() {
    if (!isRunning) {
      return;
    }
    for (AccountSynchronizer as : synchronizers.values()) {
      as.stop();
    }
    isRunning = false;
  }

  /*
  public IMAPStore getStoreForAccount(IMAPAccount account) {
  synchronized(synchronizers) {
  	final AccountSynchronizer as = synchronizers.get(account);
  	if(as == null) {
  		return null;
  	} else {
  		return as.getRemoteStore();
  	}
  }
  }
  */
}

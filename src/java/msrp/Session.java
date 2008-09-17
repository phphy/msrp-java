/* Copyright � Jo�o Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp;

import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;
import msrp.utils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.awt.RadialGradientPaint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;

/**
 * @author Jo�o Antunes
 */
public class Session
{

    /**
     * Associates an interface to the session, used to process incoming messages
     * 
     */
    private MSRPSessionListener msrpSessionListener;

    // TODO alter the constructors to use the this(argument) call

    /**
     * Constructor used to define the report mechanism for all messages (wrapper
     * for the tls relays InetAddress constructor)
     * 
     * @throws IOException
     * @throws URISyntaxException
     */
    public Session(boolean tls, boolean relays, InetAddress address,
        ReportMechanism reportMechanism)
        throws InternalErrorException
    {
        this(tls, relays, address);
        this.reportMechanism = reportMechanism;
    }

    /**
     * Constructor used to define the report mechanism for all messages (wrapper
     * for the tls, relays, destinationURI, InetAddress constructor)
     * 
     * @throws Exception
     */
    public Session(boolean tls, boolean relays, URI destinationURI,
        InetAddress address, ReportMechanism reportMechanism)
        throws Exception
    {
        this(tls, relays, destinationURI, address);
        this.reportMechanism = reportMechanism;
    }

    /**
     * Creates a new session with the address given as the _local_ endpoint the
     * associated connection will be an active one
     * 
     * @param tls an boolean telling if this is a secure connection or not
     * @param relays is it a session that uses relays?
     * @param address the configured address to use as the local endpoint
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error can be getted by printing the stacktrace
     */
    public Session(boolean tls, boolean relays, InetAddress address)
        throws InternalErrorException
    {
        try
        {
            this.address = address;
            connection = new Connection(address);
            set_relays(relays);
            // Generates the new URI and adds it to the list of URIs of the
            // connection
            if (connection != null)
            {
                uri = connection.generateNewURI();

                stackInstance.addConnection(uri, connection);
            }
        }
        catch (Exception e)
        {
            // let's wrap every exception in an InternalError one
            throw new InternalErrorException(e);
        }

    }

    /**
     * Creates a new session with the address given as the _local_ endpoint the
     * associated connection will be a passive one at the moment.
     * 
     * @param tls an boolean telling if this is a secure connection or not
     * @param relays is it a session that uses relays?
     * @param address the configured address to use as the local endpoint
     * @throws InternalErrorException if any kind of internal error ocurred,
     *             usually there was an error with the socket or the generation
     *             of the URI, more information can be obtained by printing the
     *             stacktrace
     * 
     */
    public Session(boolean tls, boolean relays, URI destinationURI,
        InetAddress address)
        throws InternalErrorException
    {
        this.address = address;
        connection = MSRPStack.getConnectionsInstance(address);
        try
        {
            uri = ((Connections) connection).generateNewUri();
        }
        catch (Exception e)
        {
            // let's wrap every exception in an InternalError one
            throw new InternalErrorException(e);
        }
        ((Connections) connection).addUriToIdentify(uri, this);
        // is the subsequent needed?! TODO
        stackInstance.addConnection(uri, connection);
        set_relays(relays);

    }

    /**
     * @param tls says if this is a secure connection or not.
     * @param failureReport sets the failure-report value
     * @param relays says if this sessions uses relays or not
     * @return Session the newly created session with the failureport altered
     */
    public Session(boolean tls, boolean failureReport, boolean relays)
    {
        super();
    }

    /**
     * @param tls tells if this session goes through a seucure connection or not
     * @param successReport sets the value of the success-Report field
     * @param failureReport sets the failure-report value
     * @param relays does this session uses relays
     * @return a newly created session
     */
    public Session(boolean tls, boolean successReport, boolean failureReport,
        boolean relays)
    {
        super();
    }

    private MSRPStack stackInstance = MSRPStack.getInstance();

    /**
     * @param connection the connection to add adds the connection to the
     *            session and to the MSRPStack
     */
    private void setConnection(Connection conn)
    {
        if (conn == null)
            IOInterface
                .debugln("Error, tried to add a null connection to the session!");

        MSRPStack.getInstance().delConnection(connection);
        connection = conn;
        MSRPStack.getInstance().addConnection(connection);
    }

    private ArrayList<URI> uris = new ArrayList<URI>();

    private TransactionManager transactionManager;

    /**
     * Method that should only be called by the transaction manager addSession
     * method
     * 
     * @param transactionManager the transactionManager to set
     */
    protected void setTransactionManager(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    /**
     * @return the transactionManager
     */
    protected TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    private InetAddress address;

    /**
     * Adds the given uri and establishes the connection according to the
     * connection model specified in the RFC
     * 
     * @param uri Receives the uri of the new connection Is used to set the new
     *            connection to remote url given by the URI
     * @return the newly or already existing connection
     * @throws URISyntaxException in case there was a problem with the given URI
     * @throws IOException if there was a problem with the socket
     */
    public void addToPath(ArrayList<URI> uris)
        throws URISyntaxException,
        IOException
    {
        for (URI uri : uris)
        {
            this.uris.add(uri);
        }
        connection.addEndPoint(this.uris.get(this.uris.size() - 1), address);
        // stackInstance.addActiveSession(this);
        transactionManager = connection.getTransactionManager();
        transactionManager.addSession(this);
        transactionManager.initialize(this);

        /*
         * //TODO: refactor the method it does somethings that don't make sense
         * anymore
         * 
         * URI newUri = new URI(uri); URL newUrl = newUri.toURL();
         * 
         * 
         * Connection newCon =
         * MSRPStack.getInstance().getConnectionByURL(newUrl); if (newCon !=
         * null) { setConnection(newCon); return _connection; } newCon = new
         * Connection(); setConnection(newCon);
         * 
         * 
         * return _connection;
         */
    }

    /**
     * Convenience method (that probably will disappear when the message id
     * generation is corrected) that searches for the given messageID on the
     * queue of messages to send
     * 
     * @param messageID String representing the messageID to search for
     * @return true if it exists on the queue or false otherwise
     */
    private boolean existsInMessagesToSend(String messageID)
    {
        for (Message message : messagesToSend)
        {
            if (message.getMessageID().equals(messageID))
                return true;
        }
        return false;
    }

    /**
     * Generates a new unique message-ID relative to this session FIXME Issue #7
     * TODO do this by the method advised on the RFC so that the message-ID is
     * always unique!
     * 
     * @return
     */
    protected String generateMessageID()
    {
        byte[] messageID = new byte[9];
        TextUtils.generateRandom(messageID);
        String newMessageID = new String(messageID, TextUtils.usascii);

        while (messagesReceive.containsKey(newMessageID)
            || messagesSent.containsKey(newMessageID)
            || existsInMessagesToSend(newMessageID))
        {
            TextUtils.generateRandom(messageID);
            newMessageID = new String(messageID, TextUtils.usascii);
        }
        return newMessageID;

    }

    public ArrayList<URI> getToPath()
    {
        return uris;
    }

    /**
     * @desc the connection associated with this session
     * @uml.property name="_connectoin"
     * @uml.associationEnd inverse="_session:msrp.Connection"
     */
    private msrp.Connection connection = null;

    /**
     * Getter of the property <tt>_connectoin</tt>
     * 
     * @return Returns the _connectoin.
     * @uml.property name="_connectoin"
     */
    public msrp.Connection getConnection()
    {
        return connection;
    }

    /**
     * @uml.property name="_failureReport"
     */
    private boolean report = true;

    /**
     * Getter of the property <tt>_failureReport</tt>
     * 
     * @return Returns the report.
     * @uml.property name="_failureReport"
     */
    public boolean is_failureReport()
    {
        return report;
    }

    /**
     * Setter of the property <tt>_failureReport</tt>
     * 
     * @param _failureReport The report to set.
     * @uml.property name="_failureReport"
     */
    public void set_failureReport(boolean report)
    {
        this.report = report;
    }

    /**
     * @uml.property name="_successReport"
     */
    private boolean report1;

    /**
     * Getter of the property <tt>_successReport</tt>
     * 
     * @return Returns the report1.
     * @uml.property name="_successReport"
     */
    public boolean is_successReport()
    {
        return report1;
    }

    /**
     * Setter of the property <tt>_successReport</tt>
     * 
     * @param _successReport The report1 to set.
     * @uml.property name="_successReport"
     */
    public void set_successReport(boolean report)
    {
        report1 = report;
    }

    /**
     * This field contains the queue of messages to be sent
     * 
     * @uml.property name="messagesToSend"
     */
    private ArrayList<Message> messagesToSend = new ArrayList<Message>();

    /**
     * @uml.property name="_messagesSent" stores the sent/being sended messages
     *               according to the Success-Report field
     */
    private HashMap<String, Message> messagesSent =
        new HashMap<String, Message>();

    /**
     * contains the messages being received
     */
    private HashMap<String, Message> messagesReceive =
        new HashMap<String, Message>();

    /**
     * Getter of the property <tt>_message</tt>
     * 
     * @return Returns the _message.
     * @uml.property name="_message"
     */
    public HashMap<String, Message> getMessagesReceive()
    {
        return messagesReceive;
    }

    /**
     * @uml.property name="_relays"
     */
    private boolean relays;

    /**
     * Getter of the property <tt>_relays</tt>
     * 
     * @return Returns the _relays.
     * @uml.property name="_relays"
     */
    public boolean is_relays()
    {
        return relays;
    }

    /**
     * Setter of the property <tt>_relays</tt>
     * 
     * @param _relays The _relays to set.
     * @uml.property name="_relays"
     */
    public void set_relays(boolean relays)
    {
        this.relays = relays;
    }

    /**
     * @uml.property name="_URI" the URI that identifies this session
     */
    private URI uri = null;

    /**
     * Getter of the property <tt>_URI</tt>
     * 
     * @return Returns the URI that uniquely identifies this session
     * @uml.property name="_URI"
     */
    public URI getURI()
    {
        return uri;
    }

    /**
     * Setter of the property <tt>_connectoin</tt>
     * 
     * @param _connectoin The _connection to set.
     * @uml.property name="_connectoin"
     */
    public void setconnection(msrp.Connection connection)
    {
        this.connection = connection;
    }

    /**
     * @uml.property name="_sessionManager"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_sessions:msrp.SessionManager"
     */
    private msrp.SessionManager manager = null;

    /**
     * this field points to the report mechanism associated with this session
     * the report mechanism basically is used to decide upon the granularity of
     * the success reports
     * 
     * it will use the DefaultReportMechanism
     */
    private ReportMechanism reportMechanism =
        DefaultReportMechanism.getInstance();

    /**
     * Getter of the property <tt>_sessionManager</tt>
     * 
     * @return Returns the manager.
     * @uml.property name="_sessionManager"
     */
    public msrp.SessionManager get_sessionManager()
    {
        return manager;
    }

    /**
     * Setter of the property <tt>_sessionManager</tt>
     * 
     * @param _sessionManager The manager to set.
     * @uml.property name="_sessionManager"
     */
    public void set_sessionManager(msrp.SessionManager manager)
    {
        this.manager = manager;
    }

    public void addMSRPSessionListener(MSRPSessionListener object)
    {
        // TODO add the needed listeners from the object
        if (object != null && object instanceof MSRPSessionListener)
        {
            msrpSessionListener = object;
        }
    }

    public String toString()
    {
        return getURI().toString();
    }

    /**
     * Adds the given message to the top of the message to send queue
     * 
     * this method is used when a message sending is paused so that when this
     * session activity get's resumed it will continue sending this message
     * 
     * @param message the message to be added to the top of the message queue
     */
    protected void addMessageOnTop(Message message)
    {
        messagesToSend.add(0, message);
    }

    /**
     * Adds the given message to the end of the message to send queue
     * 
     * @param message the message to be added to the end of the message queue
     */
    public void addMessageToSend(Message message)
    {
        messagesToSend.add(message);

    }

    /**
     * @return true if this session has messages to send false otherwise
     */
    public boolean hasMessagesToSend()
    {
        return !messagesToSend.isEmpty();
    }

    /**
     * FIXME Warning: this name is misleading:
     * 
     * retrieves a message from the sentMessages The sentMessages array may have
     * messages that are currently being sent they are only stored for REPORT
     * purposes.
     * 
     * @param messageID of the message to retrieve
     * @return the message associated with the messageID
     */
    protected Message getSentMessage(String messageID)
    {
        return messagesSent.get(messageID);

    }

    /**
     * Returns and removes the first message of the top of the messagesToSend
     * array.
     * 
     * @return the first message to be sent out of the messagesToSend array
     */
    public Message getMessageToSend()
    {
        // returns the first message to be sent out of the messagesToSend array
        // list
        if (messagesToSend.isEmpty())
            return null; // TODO ?! FIXME?! also delete from the active sessions
        Message messageToReturn = messagesToSend.get(0);
        messagesToSend.remove(messageToReturn);
        return messageToReturn;
    }

    //public void addUriToIdentify(URI uri)
    //{
        // TODO add the uri to a list in order to verify the authenticity of the
        // sender
        /*
         * if (this.connection instanceof Connections) ((Connections)
         * Connections.getInstance()).addUriToIdentify(uri,this);
         */
    //}

    /**
     * method used by an incoming transaction to retrieve the message object
     * associated with it, if it's already being received
     * 
     * @param messageID of the message to
     * @return the message being received associated with messageID or null if
     *         there is none
     * 
     */
    protected Message getReceivingMessage(String messageID)
    {
        return messagesReceive.get(messageID);

    }

    /*
     * Triggers to the Listener, not really sure if they are needed now, but
     * later can be used to trigger some extra validations before actually
     * calling the callback
     */
    /**
     * trigger for the registered MSRPSessionListener callback.
     * 
     * @see MSRPSessionListener
     * @param report the transaction associated with the report request
     */
    protected void triggerReceiveReport(Transaction report)
    {
        msrpSessionListener.receivedReport(this, report);
    }

    /**
     * trigger for the registered MSRPSessionListener callback.
     * 
     * @see MSRPSessionListener
     * @param message the received message
     */
    protected void triggerReceiveMessage(Message message)
    {
        msrpSessionListener.receiveMessage(this, message);

    }

    /**
     * trigger for the registered MSRPSessionListener callback
     * 
     * @see MSRPSessionListener
     * @param message the message to accept or not
     * @return true or false if we are accepting the message or not
     */
    protected boolean triggerAcceptHook(IncomingMessage message)
    {
        return msrpSessionListener.acceptHook(this, message);

    }

    /**
     * trigger for the registered MSRPSessionListener callback updateSendStatus
     * 
     * @see MSRPSessionListener
     */
    protected void triggerSendUpdate(Session session, Message outgoingMessage)
    {
        msrpSessionListener.updateSendStatus(session, outgoingMessage,
            outgoingMessage.bytesSent());
    }

    /*
     * End of triggers to the Listener
     */

    /**
     * @return the reportMechanism associated with this session
     */
    protected ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /**
     * This method releases all of the resources associated with this session.
     * It could eventually, but not necessarily, close connections conforming to
     * the Connection Model on RFC 4975 TODO work in progress
     */
    public void tearDown()
    {
    }

    /**
     * at this point this is used by the generation of the success report to
     * assert if it should be sent or not quoting the RFC:
     * 
     * "Endpoints SHOULD NOT send REPORT requests if they have reason to believe
     * the request will not be delivered. For example, they SHOULD NOT send a
     * REPORT request for a session that is no longer valid."
     * 
     * 
     * @return true or false depending if this is a "valid" (active?!) session
     *         or not
     */
    public boolean isActive()
    {
        // TODO Auto-generated method stub
        return true;
    }

    /**
     * Adds a message to the sent message list the message is stored because of
     * expected subsequent REPORT requests on this message
     * 
     * @param message the message to add
     */
    protected void addSentMessage(Message message)
    {
        messagesSent.put(message.getMessageID(), message);

    }

    /*
     * public Message sendMessage(String contentType, byte[] byteContent) throws
     * Exception { try { Message messageToSend = new
     * Message(this,contentType,byteContent);
     * 
     * messagesToSend.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     * 
     * 
     * public Message sendMessage(String contentType, Stream stream) throws
     * Exception { try { Message messageToSend = new
     * Message(this,contentType,stream);
     * 
     * messagesToSend.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     */

}

package net.sf.juffrou.mq.util;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.stage.Stage;
import net.sf.juffrou.mq.dom.MessageDescriptor;
import net.sf.juffrou.mq.ui.NotificationPopup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.pcf.PCFConstants;

public class MessageReceivingTask extends Task<MessageDescriptor> {

	private static final Logger log = LoggerFactory.getLogger(MessageReceivingTask.class);

	private final MQQueueManager qm;
	private final MQMessage replyMessage;
	private final String queueNameReceive;
	private final String queueNameSend;
	private final Integer brokerTimeout;

	public MessageReceivingTask(final MessageReceivedHandler handler, MQQueueManager qm, String queueNameReceive, Integer brokerTimeout, MQMessage replyMessage, String queueNameSent) {
		super();
		this.qm = qm;
		this.queueNameReceive = queueNameReceive;
		this.brokerTimeout = brokerTimeout;
		this.replyMessage = replyMessage;
		this.queueNameSend = queueNameSent;

		stateProperty().addListener(new ChangeListener<Worker.State>() {
			@Override
			public void changed(ObservableValue<? extends javafx.concurrent.Worker.State> observable, javafx.concurrent.Worker.State oldValue, javafx.concurrent.Worker.State newState) {
				switch (newState) {
				case SUCCEEDED:
					handler.messageReceived(getValue());
					break;
				case FAILED:
					MessageDescriptor messageDescriptor = new MessageDescriptor();
					messageDescriptor.setText(getMessage());
					handler.messageReceived(messageDescriptor);
					NotificationPopup popup = new NotificationPopup(handler.getStage());
					popup.display(getMessage());
					break;
				case CANCELLED:
					MessageDescriptor canceledDescriptor = new MessageDescriptor();
					canceledDescriptor.setText(getMessage());
					handler.messageReceived(canceledDescriptor);
					break;
				}
			}
		});
	}

	@Override
	protected MessageDescriptor call() throws Exception {
		MQQueue replyQueue = null;
		try {
			// Construct new MQGetMessageOptions object
			MQGetMessageOptions gmo = new MQGetMessageOptions();

			// Set the get message options.. specify that we want to wait
			// for reply message
			// AND *** SET OPTION TO CONVERT CHARS TO RIGHT CHAR SET ***
			gmo.options = MQConstants.MQGMO_WAIT | MQConstants.MQGMO_CONVERT;

			gmo.options |= MQConstants.MQGMO_PROPERTIES_FORCE_MQRFH2;
			gmo.options |= MQConstants.MQGMO_CONVERT;

			// Specify the wait interval for the message in milliseconds
			gmo.waitInterval = brokerTimeout.intValue();

			if (log.isDebugEnabled()) {
				log.debug("Current Msg ID used for receive: '" + new String(replyMessage.messageId) + "'");
				log.debug("Correlation ID to use for receive: '" + new String(replyMessage.correlationId) + "'");
				log.debug("Supported character set to use for receive: " + replyMessage.characterSet);
			}

			// If the name of the request queue is the same as the reply
			// queue...(again...)
			int openOptions;
			if (queueNameReceive.equals(queueNameSend)) {
				openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT; // Same options as out
																							// bound queue
			} else {
				openOptions = MQConstants.MQOO_INPUT_AS_Q_DEF; // in bound
																// options
																// only
			}
			// openOptions |= MQConstants.MQOO_READ_AHEAD;
			replyQueue = qm.accessQueue(queueNameReceive, openOptions, null, // default q manager
					null, // no dynamic q name
					null); // no alternate user id

			if (isCancelled())
				return null;

			// Following test lines will cause any message on the queue to
			// be received regardless of
			// whatever message ID or correlation ID it might have
			// replyMessage.messageId = MQConstants.MQMI_NONE;
			// replyMessage.correlationId = MQConstants.MQCI_NONE;

			//				replyMessage.characterSet = 1208; // UTF-8 (will be charset=819 when the msg has Portuguese accented chars)

			replyMessage.format = MQConstants.MQFMT_RF_HEADER_2;
			// replyMessage.setBooleanProperty(MQConstants.WMQ_MQMD_READ_ENABLED,
			// true);

			// The replyMessage will have the correct correlation id for the
			// message we want to get.
			// Get the message off the queue..
			replyQueue.get(replyMessage, gmo);
			// And prove we have the message by displaying the message text
			System.out.println("The receive message character set is: " + replyMessage.characterSet);

			MessageDescriptor replyMessageDescriptor = MessageDescriptorHelper.createMessageDescriptor(replyMessage);

			return replyMessageDescriptor;

		} catch (MQException mqe) {
			if (log.isErrorEnabled())
				log.error("Error receiving message " + mqe + ": " + PCFConstants.lookupReasonCode(mqe.reasonCode));
			updateMessage("Error receiving message " + mqe + ": " + PCFConstants.lookupReasonCode(mqe.reasonCode));
			throw mqe;
		} catch (java.io.IOException ex) {
			if (log.isErrorEnabled())
				log.error("Error receiving message " + ex.getMessage());
			updateMessage(ex.getMessage());
			throw ex;
		} catch (Exception ex) {
			if (log.isErrorEnabled())
				log.error("Error receiving message " + ex.getMessage());
			updateMessage(ex.getMessage());
			throw ex;
		} finally {
			if (replyQueue != null)
				try {
					replyQueue.close();
				} catch (MQException e) {
					if (log.isErrorEnabled())
						log.error("Error closing queue " + e.getMessage());
				}
		}
	}

	public interface MessageReceivedHandler {
		void messageReceived(MessageDescriptor messageDescriptor);

		Stage getStage();
	}
}
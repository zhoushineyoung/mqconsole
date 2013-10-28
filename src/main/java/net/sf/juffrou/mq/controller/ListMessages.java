package net.sf.juffrou.mq.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.Stage;
import net.sf.juffrou.mq.dom.MessageDescriptor;
import net.sf.juffrou.mq.ui.Main;
import net.sf.juffrou.mq.ui.SpringFxmlLoader;
import net.sf.juffrou.mq.util.MessageDescriptorHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.ibm.mq.MQC;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.pcf.PCFConstants;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ListMessages implements Initializable {

	private static final int MAX_TEXT_LEN_DISPLAY = 160;

	@FXML
	private TableView<MessageDescriptor> table;

	@Autowired
	@Qualifier("mqQueueManager")
	private MQQueueManager qm;

	private String queueName;

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public MQQueueManager getQm() {
		return qm;
	}

	public void setQm(MQQueueManager qm) {
		this.qm = qm;
	}

	public void contextMenuRequested(ContextMenuEvent event) {
		System.out.println("menu requested");
	}

	public void openMessage(ActionEvent event) {
		ObservableList<TablePosition> cells = table.getSelectionModel().getSelectedCells();
		for (TablePosition<?, ?> cell : cells) {
			MessageDescriptor message = table.getItems().get(cell.getRow());

			SpringFxmlLoader springFxmlLoader = new SpringFxmlLoader(Main.applicationContext);
			Parent root = (Parent) springFxmlLoader.load("/net/sf/juffrou/mq/ui/message-read.fxml");

			MessageViewControler controller = springFxmlLoader.<MessageViewControler> getController();
			controller.setMessageDescriptor(message);
			controller.initialize();

			Scene scene = new Scene(root, 768, 480);
			Stage stage = new Stage();
			stage.setScene(scene);
			stage.setTitle("Message");
			stage.show();
		}
		System.out.println("Context menu clicked");
	}

	private List<MessageDescriptor> listMessages() {
		List<MessageDescriptor> messageList = new ArrayList<MessageDescriptor>();

		try {
			MQException.log = null;
			MQQueue queue = qm.accessQueue(queueName, MQC.MQOO_BROWSE | MQC.MQOO_FAIL_IF_QUIESCING);
			MQMessage message = new MQMessage();
			MQGetMessageOptions gmo = new MQGetMessageOptions();

			gmo.options = MQC.MQGMO_BROWSE_NEXT | MQC.MQGMO_NO_WAIT | MQC.MQGMO_CONVERT;

			while (true) {
				message.messageId = null;
				message.correlationId = null;
				queue.get(message, gmo);

				MessageDescriptor messageDescriptor = MessageDescriptorHelper.createMessageDescriptor(message);

				messageList.add(messageDescriptor);

				// Parse the message content using a PCFMessage object and print out the result.
				//				PCFMessage pcf = new PCFMessage(message);
				//				System.out.println("Message " + ++messageCount + ": " + pcf + "\n");
			}
		}

		catch (MQException mqe) {
			if (mqe.reasonCode == MQException.MQRC_NO_MSG_AVAILABLE) {
				System.out.println(messageList.size() + (messageList.size() == 1 ? " message." : " messages."));
			} else {
				System.err.println(mqe + ": " + PCFConstants.lookupReasonCode(mqe.reasonCode));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (MQDataException e) {
			System.err.println(e + ": " + PCFConstants.lookupReasonCode(e.reasonCode));
		}

		return messageList;
	}

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
	}

	public void initialize() {
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

		ObservableList<MessageDescriptor> rows = FXCollections.observableArrayList();
		rows.addAll(listMessages());
		table.setItems(rows);
	}
}

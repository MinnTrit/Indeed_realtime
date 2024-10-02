import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.NetworkClient;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.net.InetAddress;
import java.util.Properties;

public class Producer {
	private KafkaProducer producer;
	 public Producer() {
		 this.producer = initialize();
	 }
	 
	 public KafkaProducer initialize() {
		 String bootstrapServer = "{host_name}:9092";
		 Properties props = new Properties();
		 props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
		 props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		 props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
		 props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
//		 props.setProperty(ProducerConfig.CLIENT_ID_CONFIG, InetAddress.getLocalHost().getHostName());
		 KafkaProducer<String, String> producer = new KafkaProducer<>(props);
		 return producer;
	 }
	 
	 private String convertJsonString(Map<String, String> inputMap) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			String jsonString = objectMapper.writeValueAsString(inputMap);
			return jsonString;
		}
		catch (Exception e) {
			System.out.println("Failed converting the value for "+inputMap);
			return "Not found";
		}
	 };
	 
	 public void toSendMessage(String key, Map<String, String> value) {
		 String sentTopics = "jobs-data";
		 String jsonValueString = convertJsonString(value);
		 ProducerRecord<String,String> record = new ProducerRecord<>(sentTopics, key, jsonValueString);
		 producer.send(record, (metadata, exception) -> {
	            if (exception != null) {
	                exception.printStackTrace();
	            } else {
	                System.out.printf("Sent message to topic %s partition %d with offset %d%n",
	                    metadata.topic(), metadata.partition(), metadata.offset());
	            }
	        });
		 producer.flush();
	}
	
	public void closeProducer() {
		producer.close();
		System.out.println("Closed the producer");
	}
}

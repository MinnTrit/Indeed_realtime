import org.apache.kafka.clients.consumer.KafkaConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.streaming.api.java.*;
import org.apache.spark.streaming.kafka010.*;
import static org.apache.spark.streaming.Durations.seconds;
import java.util.Map;
import java.util.HashMap;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.*;
import org.apache.spark.api.java.function.*;
import java.io.Serializable;


public class Consumer implements Serializable {
	private transient JavaDStream<ConsumerRecord<String, String>> kafkaDStream;
	private transient JavaStreamingContext streamingContext;
	private transient SparkSession spark;
	public Consumer() {
		this.kafkaDStream = initialize();
	}
	
	private JavaDStream<ConsumerRecord<String, String>> initialize() {
		SparkConf sparkConf = new SparkConf()
				.set("spark.mongodb.write.connection.uri", "mongodb+srv://triethoangminh555:Bautroixanh12345@firstcluster.6imdogz.mongodb.net/Learning?retryWrites=true&w=majority")
				.setAppName("Kafka-Spark-Streaming")
				.setMaster("local[*]");
		JavaSparkContext sparkContext = new JavaSparkContext(sparkConf);
		SparkSession spark = SparkSession.builder().config(sparkConf).getOrCreate();
		this.spark = spark;
		this.streamingContext = new JavaStreamingContext(sparkContext, seconds(10));
		String bootstrapServers = "129.151.180.13:9092";
		String groupId = "test-group";
		String topic = "jobs-data";
		
		//Set up the Kafka Parameters
        Map<String, Object> kafkaParams = new HashMap<>();
        kafkaParams.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        kafkaParams.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        kafkaParams.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        kafkaParams.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        kafkaParams.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kafkaParams.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        kafkaParams.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        // Create a DStream from Kafka
        JavaDStream<ConsumerRecord<String, String>> kafkaDStream = KafkaUtils.createDirectStream(
            streamingContext,
            LocationStrategies.PreferConsistent(),
            ConsumerStrategies.Subscribe(Collections.singletonList(topic), kafkaParams)
        );
        
        return kafkaDStream;
	}
	
	private StructType makeStructType() {
		StructField[] jobsField = new StructField[] {
			DataTypes.createStructField("SearchWord", DataTypes.StringType, true),
			DataTypes.createStructField("Job", DataTypes.StringType, true),
			DataTypes.createStructField("Company", DataTypes.StringType, true),
			DataTypes.createStructField("Salary", DataTypes.StringType, true),
			DataTypes.createStructField("Location", DataTypes.StringType, true),
			DataTypes.createStructField("Deadline", DataTypes.StringType, true),
			DataTypes.createStructField("JobsDetails", DataTypes.StringType, true),
			DataTypes.createStructField("Created", DataTypes.StringType, true)
		};
		StructType jobsType = new StructType(jobsField);
		return jobsType;
	};
	
	public void consumeMessage() {
		System.out.println("Received the DStream at "+kafkaDStream);
		StructType jobsType = makeStructType();
		kafkaDStream.foreachRDD(rdd -> {
	        rdd.foreach(record -> {
	            System.out.printf("Consumed message: key = %s, value = %s, partition = %d, offset = %d%n",
	                    record.key(), record.value(), record.partition(), record.offset());
	        });
	        Function<ConsumerRecord<String, String>, Row> toRow = new Function<ConsumerRecord<String, String>, Row>() {
	        	@Override
	        	public Row call(ConsumerRecord<String, String> inputRecord) {
	        		String value = inputRecord.value();
	        		ObjectMapper objectMapper = new ObjectMapper();
	        		try {
	        			JsonNode jsonNode = objectMapper.readTree(value);
	        			String searchWord = jsonNode.get("SearchKeyWord").asText();
	        			String jobText = jsonNode.get("Job").asText();
	        			String companyText = jsonNode.get("Company").asText();
	        			String salaryText = jsonNode.get("Salary").asText();
	        			String locationText = jsonNode.get("Location").asText();
	        			String deadlineText = jsonNode.get("Deadline").asText();
	        			String jobsDetailText = jsonNode.get("JobDetails").asText();
	        			String createdText = jsonNode.get("Created").asText();
	        			return RowFactory.create(searchWord, jobText, companyText, salaryText, 
	        					locationText, deadlineText, jobsDetailText, createdText);}
	        		catch (Exception e) {
	        			System.out.println("Error occured while parsing");
	        			return RowFactory.create(null, null, null,
	        					null, null, null, null, null);
	        		}
	        	}
	        };
	        JavaRDD<Row> rowRDD = rdd.map(toRow);
	        Dataset<Row> df = spark.createDataFrame(rowRDD, jobsType);
	        df.write()
	          .format("mongo")
	          .mode("append")
	          .option("uri", "mongodb+srv://triethoangminh555:Bautroixanh12345@firstcluster.6imdogz.mongodb.net/Learning?retryWrites=true&w=majority")
	          .option("collection", "jobsData")
	          .option("database", "Learning")
	          .save();
	        System.out.println("The total records in the RDD being consumed is at "+rdd.count());
	        System.out.println("Done saving the data to the database");
	    	Pusher myPusher = new Pusher();
	    	myPusher.toPush("Data!A1:Z", "1GSlr3En-DmJwWuFVWlsP-WTuYclYbsR9sjKeJc9qt60");
	    });
	    // Start the computation
        streamingContext.start();

        // Await termination
        try {
        	streamingContext.awaitTermination();
        }
        catch (InterruptedException e) {
        	System.out.println("Error occured as "+e.getMessage());
        }
        finally {
        	streamingContext.stop(true, true);
        }
    }

};


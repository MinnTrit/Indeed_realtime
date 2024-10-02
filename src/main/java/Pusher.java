import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.spark.sql.functions.*;
import org.apache.spark.sql.types.*;
import org.apache.spark.sql.*;
import org.apache.spark.api.java.*;
import org.apache.spark.api.java.function.*;
import org.apache.spark.SparkConf;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.JsonFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Pusher {
	private String connectionString = "";
	private String databaseName = "";
	private String collectionName = "";
	private Dataset<Row> df;
	public Pusher() {
		initialize();
	}
	
	private Dataset<Row> toDataframe(List<ObjectNode> inputList) {
		SparkConf conf = new SparkConf().setMaster("local[*]");
		SparkSession spark = SparkSession.builder().config(conf).getOrCreate();
		JavaSparkContext sc = new JavaSparkContext(spark.sparkContext());
		StructField[] jobsField = new StructField[] {
				DataTypes.createStructField("SearchWord", DataTypes.StringType, true),
				DataTypes.createStructField("Job", DataTypes.StringType, true),
				DataTypes.createStructField("Company", DataTypes.StringType, true),
				DataTypes.createStructField("Salary", DataTypes.StringType, true),
				DataTypes.createStructField("Location", DataTypes.StringType, true),
				DataTypes.createStructField("Deadline", DataTypes.StringType, true),
				DataTypes.createStructField("JobsDetails", DataTypes.StringType, true),
				DataTypes.createStructField("Created", DataTypes.StringType, true),
				DataTypes.createStructField("QueryAt", DataTypes.StringType, true)
		};
		StructType schema = new StructType(jobsField);
		JavaRDD<ObjectNode> objectRdd = sc.parallelize(inputList);
		
		LocalDateTime currentDateTime = LocalDateTime.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		String dateString = currentDateTime.format(formatter);
		JavaRDD<Row> rdd = objectRdd.map(row -> {
		JsonNode jsonSearchWord = row.get("SearchWord");
		String searchWord;
		if (jsonSearchWord != null) {
			searchWord = jsonSearchWord.asText();
			String job = row.get("Job").asText();
			String company = row.get("Company").asText();
			String salary = row.get("Salary").asText();
			String location = row.get("Location").asText();
			String deadLine = row.get("Deadline").asText();
			String jobDetails = row.get("JobsDetails").asText();
			String created = row.get("Created").asText();
			return RowFactory.create(searchWord, job, company, salary, 
					location, deadLine, jobDetails, created, dateString);
		}
		else {
			return RowFactory.create(null, null, null, null, 
					null, null, null, null, null);
		}
		});
		
		Dataset<Row> df = spark.createDataFrame(rdd, schema);
		df = df.filter(df.col("SearchWord").isNotNull());
//		df = df.dropDuplicates();
		return df;
	};
	
	private void initialize() {
		List<ObjectNode> resultList = new ArrayList<>();
		try (MongoClient mongoClient = MongoClients.create(connectionString)) {
			ObjectMapper objectMapper = new ObjectMapper();
			MongoDatabase database = mongoClient.getDatabase(databaseName);
			MongoCollection<Document> collection = database.getCollection(collectionName);
			try (MongoCursor<Document> cursor = collection.find().iterator()) {
				while (cursor.hasNext()) {
					Document doc = cursor.next();
					String jsonString = doc.toJson();
					try {
						JsonNode jsonObj = objectMapper.readTree(jsonString);
						if (jsonObj.isObject()) {
							ObjectNode objectNode = (ObjectNode) jsonObj;
							objectNode.remove("_id");
							resultList.add(objectNode);
						}
					}
					catch (Exception e) {
						System.out.println("Failed converting the string of "+jsonString);
					}
										
				}
			}
			this.df = toDataframe(resultList);
		}
	}
	
	public void showDf() {
		System.out.println("The current dataframe is at");
		df.show();
	}
	
	public void toPush(String inputRange, String spreadsheetId) {
		String applicationName = "Kafka-Spreadsheet";
		try {
			File file = new File(getClass().getResource("/googleauth.json").toURI());
			FileInputStream fis = new FileInputStream(file);
			GoogleCredentials credentials = GoogleCredentials.fromStream(fis);
			HttpTransport httpTransported = GoogleNetHttpTransport.newTrustedTransport();
			JsonFactory gsonFactory = GsonFactory.getDefaultInstance();
			HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(credentials);
	        Sheets sheetsService = new Sheets.Builder(httpTransported, gsonFactory, adapter)
	                .setApplicationName(applicationName)
	                .build();
	        List<List<Object>> data = new ArrayList<>();
	        String[] columnsList = df.columns();
	        data.add(Arrays.asList(columnsList));
	        
	        List<Row> rowsList = df.collectAsList();
	        for (Row row : rowsList) {
	        	List<Object> rowData = new ArrayList<>();
	        	for (String column : columnsList) {
	        		Object cellValue = row.getAs(column);
	        		rowData.add(cellValue);
	        	}
	        	data.add(rowData);
	        }
	        ValueRange updatedRange = new ValueRange().setValues(data);
	        sheetsService.spreadsheets().values()
	        			.update(spreadsheetId, inputRange, updatedRange)
	        			.setValueInputOption("USER_ENTERED")
	        			.execute();
			System.out.println("Done pushing the data to spreadsheet");
		}
		catch (Exception e) {
			System.out.println("Error while reading the file as "+e.getMessage());
		}
		
	};
}

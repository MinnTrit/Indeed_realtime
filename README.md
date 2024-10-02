### Sample Video
https://github.com/user-attachments/assets/fa66d649-b162-4961-9066-155446db12bd

### Overall Diagram
![image](https://github.com/user-attachments/assets/bce8fce8-adac-49d0-828c-53bd410a5e20)

### Diagram breakdown
1. ```Scrapper.java``` initialized scrapping jobs: The ```Playwright Scrapper``` will initialize the scrapping jobs collect data from Indeed websites, the result of the scrapping will have the data structure of ```List<Map<String, String>>```, where each ```Map<String, String>``` represents for the ```Scrape source``` and ```Scrape value``` respectively.
2. ```Producer.java``` receives the output and sends message to the ```Kafka cluster```: For each ```Map<String, String>``` received from the ```Scrapper.java```, the ```Producer.java``` will start sending these outputs in stream to the ```Kafka Cluster```'s partitions of the predefined topic
3. ```Consumer.java``` listens on the ```Kafka Cluster``` to process the message: In this case of Kafka, the normal consumer will be replaced with ```Spark Executors``` to leverage its data manipulations along with Spark's abilities to handle data. ```Consumer.java``` will be executed in its own thread -> This can be packacged to the ```Docker's imnage``` and have the consumer's code host on the cloud to make it effectively listen for upcoming messages sent by the producer. The ```Consumer.java``` mainly do 2 things:
   * Save the scrapped data received from the ```Producer.java``` to the ```MongoDB Atlas``` => Can be later used by Software Development team to build internal website for scrapped data
   * Leverage the ```Pusher.java``` instance to push the data to the destinated spreadsheet through using ```Google API Client``` => Can be later used by Analytics Team to analyze or visualize the data
4. ```Grafana``` visualizes the pasted data from the spreadsheet to monitor in realtime: After the data has been pasted to the spreadsheet, ```Grafana``` will effectively refresh per 5 seconds to update the latest data

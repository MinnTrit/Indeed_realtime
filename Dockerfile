# Use the official Java image
FROM adoptopenjdk/openjdk8

# Install necessary tools
# RUN apt-get update && apt-get install -y wget

# Download Hadoop binaries (including native libraries)
#RUN wget https://downloads.apache.org/hadoop/common/hadoop-3.3.6/hadoop-3.3.6.tar.gz && \
#   tar -xzvf hadoop-3.3.6.tar.gz && \
#    mv hadoop-3.3.6 /usr/local/hadoop

# Set environment variables for Hadoop
#ENV HADOOP_HOME=/usr/local/hadoop
#ENV LD_LIBRARY_PATH=$HADOOP_HOME/lib/native:$LD_LIBRARY_PATH

# Set the working directory
WORKDIR /app

# Copy the JAR file from the target directory into the image
COPY target/kafka-learning-0.0.1-SNAPSHOT-jar-with-dependencies.jar app.jar

# Specify the command to run the application
CMD ["java", "-jar", "app.jar"]


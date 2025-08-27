package dlq.issue

import java.util.concurrent.TimeUnit
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.api.DeadLetterPolicy
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionType
import org.apache.pulsar.common.policies.data.Policies
import org.apache.pulsar.common.policies.data.SchemaCompatibilityStrategy
import org.testcontainers.containers.PulsarContainer
import org.testcontainers.utility.DockerImageName

val NS = "public/test-ns" + System.currentTimeMillis()

val TOPIC = NS + "/test-topic"
val DLQ_TOPIC = NS + "/dlq-topic"
val RETRY_TOPIC = NS + "/retry-topic"

fun main() {
    val fullImageName = System.getProperty("PULSAR_DOCKER_IMAGE", "apachepulsar/pulsar:latest")
    PulsarContainer(DockerImageName.parse(fullImageName)).use { pulsar ->
        pulsar.start()

        val admin = PulsarAdmin.builder().serviceHttpUrl(pulsar.httpServiceUrl).build()
        val client = PulsarClient.builder().serviceUrl(pulsar.pulsarBrokerUrl).build()

        // create a new namespace with SchemaCompatibilityStrategy.ALWAYS_COMPATIBLE
        var policies = Policies()
        policies.schema_compatibility_strategy = SchemaCompatibilityStrategy.ALWAYS_COMPATIBLE
        admin.namespaces().createNamespace(NS, policies)

        client
            .newConsumer(Schema.AVRO(SomeRecord::class.java))
            .topic(TOPIC)
            .subscriptionType(SubscriptionType.Shared)
            .subscriptionName("test-subscription")
            .enableRetry(false)
            .deadLetterPolicy(
                DeadLetterPolicy.builder()
                    .deadLetterTopic(DLQ_TOPIC)
                    .retryLetterTopic(RETRY_TOPIC)
                    .maxRedeliverCount(1)
                    .build()
            )
            .ackTimeoutTickTime(500, TimeUnit.MILLISECONDS)
            .acknowledgmentGroupTime(10, TimeUnit.MILLISECONDS)
            .negativeAckRedeliveryDelay(1, TimeUnit.SECONDS)
            .messageListener { consumer, msg -> consumer.negativeAcknowledge(msg) }
            .subscribe()

        val producer =
            client.newProducer(Schema.AVRO(IncompatibleRecord::class.java)).topic(TOPIC).create()
        producer.send(IncompatibleRecord("test"))

        Thread.sleep(10000)
        println("Shutting down...")
        System.exit(0)
    }
}

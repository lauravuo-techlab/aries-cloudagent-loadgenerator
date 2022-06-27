package com.bka.ssi.generator.agents.acapy

import com.bka.ssi.generator.application.logger.ErrorLogger
import com.bka.ssi.generator.domain.objects.*
import com.bka.ssi.generator.domain.services.IAriesClient
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.findy_network.findy_common_kt.*
import org.hyperledger.aries.api.connection.ConnectionRecord
import org.hyperledger.aries.api.connection.ConnectionState
import org.hyperledger.aries.api.credentials.CredentialAttributes
import org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange
import org.hyperledger.aries.api.present_proof.PresentProofRequest
import org.hyperledger.aries.api.present_proof.PresentationExchangeRecord
import org.hyperledger.aries.api.present_proof.PresentationExchangeState
import org.hyperledger.aries.api.present_proof.PresentationRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

data class Invitation(
    @SerializedName(value = "@type") val type: String,
    @SerializedName(value = "@id") val id: String,
    val serviceEndpoint: String,
    val recipientKeys: List<String>,
    val label: String,
)

fun currentTimeStr(): String {
  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS"
  val stringBuffer = StringBuffer()
  val now = Date()

  val simpleDateFormat = SimpleDateFormat(dateFormat)
  simpleDateFormat.format(now, stringBuffer, FieldPosition(0))
  return stringBuffer.toString() + "000"
}

class FindyAriesClient(
    private val holder: Boolean,
    private val errorLogger: ErrorLogger,
    private val testAuthUrl: String =
        if (holder) "http://holder-acapy-auth:8889" else "http://issuer-verifier-acapy-auth:8888",
    private val testAuthOrigin: String =
        if (holder) "http://localhost:8889"
        else "http://localhost:8888", // needs to be localhost for http-protoocl
    private val testUserName: String =
        if (holder) "holder-" + System.currentTimeMillis()
        else "issuer-" + System.currentTimeMillis(),
    private val testSeed: String = "",
//        System.getenv("SEED")
//            ?: "Z7wnGIbb6amj4mMGmkeCER5zD75VYYgC", // UUID.randomUUID().toString().substring(0,32),
    private val testKey: String =
        System.getenv("KEY") ?: "15308490f1e4026284594dd08d31291bc8ef2aeac730d0daf6ff87bb92d4336c",
    private val server: String = if (holder) "holder-acapy" else "issuer-verifier-acapy",
    private val port: Int = if (holder) 50052 else 50051,
    private val certPath: String = "/grpc",
    private val connection: Connection =
        Connection(
            authUrl = testAuthUrl,
            authOrigin = testAuthOrigin,
            userName = testUserName,
            seed = testSeed,
            key = testKey,
            server = server,
            port = port,
            certFolderPath = certPath),
) : IAriesClient {

  var logger: Logger = LoggerFactory.getLogger(FindyAriesClient::class.java)

  private lateinit var publisher: AcaPyPublisher

  init {
    runBlocking { connection.agentClient.useAutoAccept() }
    // Start listening status notifications
    GlobalScope.launch {
      connection.agentClient.listen().collect {
          // connection -> INVITATION: ACTIVE
          // issuer -> OFFER_SENT: CREDENTIAL_ACKED
          // proof -> REQUEST_SENT: VERIFIED
          val status = it.notification
          try {
            if (it.notification.typeID != Notification.Type.KEEPALIVE) {
              //println("$testUserName received from Agency:\n$it")
                //println("$testUserName ${status.protocolType} ${status.role}")
            }

            when (status.typeID) {
              Notification.Type.STATUS_UPDATE -> {
                  var protocolStatus = connection.protocolClient.status(status.protocolID)

                  //println("$testUserName protocol status:\n$protocolStatus")

                  if (protocolStatus.state.state == ProtocolState.State.OK) {
                      when (status.protocolType) {

                          // New connection established
                          Protocol.Type.DIDEXCHANGE -> {
                              // wait until connection response is handled
                              if (status.role == Protocol.Role.INITIATOR) {
                                  val conn = ConnectionRecord()
                                  conn.connectionId = status.connectionID
                                  conn.updatedAt = currentTimeStr()
                                  conn.state = ConnectionState.ACTIVE
                                  publisher.handleConnection(conn)
                              }
                          }

                          Protocol.Type.ISSUE_CREDENTIAL -> {
                              if (status.role == Protocol.Role.ADDRESSEE) {
                                  val cred = V1CredentialExchange()
                                  cred.credentialExchangeId = status.protocolID
                                  cred.connectionId = status.connectionID
                                  cred.updatedAt = currentTimeStr()
                                  cred.state = CredentialExchangeState.CREDENTIAL_ACKED
                                  cred.credentialOfferDict = V1CredentialExchange.CredentialOfferDict()
                                  cred.credentialOfferDict.credentialPreview = V1CredentialExchange.CredentialProposalDict.CredentialProposal()
                                  cred.credentialOfferDict.credentialPreview.attributes = ArrayList()
                                  cred.credentialOfferDict.credentialPreview.attributes.add(
                                      CredentialAttributes(
                                          protocolStatus.issueCredential.attributes.getAttributes(0).name,
                                          protocolStatus.issueCredential.attributes.getAttributes(0).value))
                                  publisher.handleCredential(cred)
                             }

                          }

                          Protocol.Type.PRESENT_PROOF -> {
                              if (status.role == Protocol.Role.INITIATOR) {
                                  val sessionId = protocolStatus.presentProof.proof.attributesList[0].value
                                  val proof = PresentationExchangeRecord()
                                  proof.presentationExchangeId = status.protocolID
                                  proof.updatedAt = currentTimeStr()
                                  proof.connectionId = status.connectionID
                                  proof.state = PresentationExchangeState.VERIFIED
                                  proof.presentationRequest = PresentProofRequest.ProofRequest()
                                  proof.presentationRequest.name = "Expected to be valid (sessionId: $sessionId revocationRegistryId: null revocationRegistryIndexPrefix: null)"
                                  proof.isVerified = true
                                  //println("proof ok $proof")
                                  publisher.handleProof(proof)
                              }
                          }

                          else -> println("no handler for protocol type: ${status.protocolType}")
                      }

                  } else {
                      println("NOK protocol status:\n$protocolStatus")
                  }
              }
              else -> println("no handler for notification type: ${status.typeID}")
            }
          } catch (exp: Exception) {
              println(exp)
          }
      }

    }
  }

  override fun setPublisher(publisher: AcaPyPublisher) {
    this.publisher = publisher
  }

  override fun getPublicDid(): String? {
    return "VfHmVDbSvAdnM7Ph2PFh2a" // publicDID
  }

  override fun createSchemaAndCredentialDefinition(
      schemaDo: SchemaDo,
      revocable: Boolean,
      revocationRegistrySize: Int
  ): CredentialDefinitionDo {
    if (revocable) {
      throw NotImplementedError("Revocation not implemented yet.")
    }
    var credDefId = ""

    runBlocking {
      val schema =
          connection.agentClient.createSchema(
              name = schemaDo.name, attributes = schemaDo.attributes, version = schemaDo.version)

      Thread.sleep(1_000 * 30)
      val credDef = connection.agentClient.createCredDef(schemaId = schema.id, tag = "1.0")
      credDefId = credDef.id
      Thread.sleep(1_000 * 5)
    }

    return CredentialDefinitionDo(credDefId)
  }

  override fun createConnectionInvitation(alias: String): ConnectionInvitationDo {
      //println("Creating invitation to $alias")
    var invitationType = ""
    var invitationId = ""
    var recipientKeys: List<String> = ArrayList<String>()
    var serviceEndpoint = ""
    var label = ""
    runBlocking {
      val invitation = connection.agentClient.createInvitation(label = alias)
      val json = invitation.json
      var obj = Gson().fromJson(json, Invitation::class.java)

      invitationType = obj.type
      invitationId = obj.id
      recipientKeys = obj.recipientKeys
      serviceEndpoint = obj.serviceEndpoint
      label = obj.label
    }

    val conn = ConnectionRecord()
    conn.connectionId = invitationId
    conn.updatedAt = currentTimeStr()
    conn.state = ConnectionState.INVITATION
    publisher.handleConnection(conn)

      //println("Invitation created to $alias")
    return ConnectionInvitationDo(
        invitationType, invitationId, recipientKeys, serviceEndpoint, label)
  }

  override fun receiveConnectionInvitation(connectionInvitationDo: ConnectionInvitationDo) {
    val invitation =
        Invitation(
            type = connectionInvitationDo.type,
            id = connectionInvitationDo.id,
            recipientKeys = connectionInvitationDo.recipientKeys,
            serviceEndpoint = connectionInvitationDo.serviceEndpoint,
            label = connectionInvitationDo.label)
    val json = Gson().toJson(invitation)
      //println("Receiving connection invitation $json")
    runBlocking {
      connection.protocolClient.connect(invitationURL = json, label = "FindyAriesClient")
    }
  }

  override fun issueCredentialToConnection(connectionId: String, credentialDo: CredentialDo) {
      //println("Issuing credential to $connectionId")
    runBlocking {
      val protocolID = connection.protocolClient.sendCredentialOffer(
          connectionId = connectionId,
          credDefId = credentialDo.credentialDefinitionId,
          attributes = credentialDo.claims,
      )
        val cred = V1CredentialExchange()
        cred.credentialExchangeId = protocolID.id
        cred.connectionId = connectionId
        cred.updatedAt = currentTimeStr()
        cred.state = CredentialExchangeState.OFFER_SENT
        cred.credentialOfferDict = V1CredentialExchange.CredentialOfferDict()
        cred.credentialOfferDict.credentialPreview = V1CredentialExchange.CredentialProposalDict.CredentialProposal()
        cred.credentialOfferDict.credentialPreview.attributes = ArrayList()
        val firstName = credentialDo.claims.keys.first()
        val firstValue = credentialDo.claims[firstName]
        cred.credentialOfferDict.credentialPreview.attributes.add(CredentialAttributes(firstName, firstValue))
        publisher.handleCredential(cred)
    }
  }

  private fun revokeCredential(
      credentialRevocationRegistryRecord: CredentialRevocationRegistryRecordDo,
      publish: Boolean
  ) {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.revokeCredential: revocation not implemented yet.")
    throw NotImplementedError("Revocation not implemented yet.")
  }

  override fun revokeCredentialWithoutPublishing(
      credentialRevocationRegistryRecord: CredentialRevocationRegistryRecordDo
  ) {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.revokeCredentialWithoutPublishing: revocation not implemented yet.")
    throw NotImplementedError("Revocation not implemented yet.")
  }

  override fun revokeCredentialAndPublishRevocations(
      credentialRevocationRegistryRecord: CredentialRevocationRegistryRecordDo
  ) {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.revokeCredentialAndPublishRevocations: revocation not implemented yet.")
    throw NotImplementedError("Revocation not implemented yet.")
  }

  override fun createOobCredentialOffer(credentialDo: CredentialDo): OobCredentialOfferDo {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.createOobCredentialOffer: Creating an OOB Credential Offer is not implemented yet.")
    throw NotImplementedError("Creating an OOB Credential Offer is not implemented yet.")
  }

  override fun receiveOobCredentialOffer(oobCredentialOfferDo: OobCredentialOfferDo) {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.receiveOobCredentialOffer: Receiving an OOB Credential Offer is not implemented yet.")
    throw NotImplementedError("Receiving an OOB Credential Offer is not implemented yet.")
  }

  override fun sendProofRequestToConnection(
      connectionId: String,
      proofRequestDo: ProofRequestDo,
      checkNonRevoked: Boolean,
      comment: ProofExchangeCommentDo
  ) {
    if (checkNonRevoked) {
      errorLogger.reportAriesClientError(
          "FindyAriesClient.sendProofRequestToConnection: revocation not implemented yet.")
      throw NotImplementedError("Revocation not implemented yet.")
    }

    runBlocking {
      val attributes = ArrayList<ProofRequestAttribute>()
      proofRequestDo.requestedCredentials.forEach { credentialRequestDo: CredentialRequestDo ->
        attributes.addAll(
            credentialRequestDo.claims.map {
              ProofRequestAttribute(
                  name = it, credDefId = credentialRequestDo.credentialDefinitionIdRestriction)
            })
      }
        val protocolID = connection.protocolClient.sendProofRequest(
          connectionId = connectionId,
          attributes = attributes,
      )
        val proof = PresentationExchangeRecord()
        proof.presentationExchangeId = protocolID.id
        proof.updatedAt = currentTimeStr()
        proof.connectionId = connectionId
        proof.state = PresentationExchangeState.REQUEST_SENT
        proof.presentationRequest = PresentProofRequest.ProofRequest()
        proof.presentationRequest.name = comment.toString()
        proof.isVerified = false
        publisher.handleProof(proof)
    }
  }

  override fun createOobProofRequest(
      proofRequestDo: ProofRequestDo,
      checkNonRevoked: Boolean
  ): OobProofRequestDo {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.createOobProofRequest: Creating an OOB Proof Request is not implemented yet.")
    throw NotImplementedError("Creating an OOB Proof Request is not implemented yet.")
  }

  override fun receiveOobProofRequest(oobProofRequestDo: OobProofRequestDo) {
    errorLogger.reportAriesClientError(
        "FindyAriesClient.receiveOobProofRequest: Receiving an OOB Proof Request is not implemented yet.")
    throw NotImplementedError("Receiving an OOB Proof Request is not implemented yet.")
  }
}

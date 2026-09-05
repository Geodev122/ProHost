
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "PropertyName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RedundantCompanionReference",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "unused",
)

package com.example.data.dataconnect



public interface UpsertWhishTransactionMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertWhishTransactionMutation.Data,
      UpsertWhishTransactionMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val orderId: String,
  
    val amountUsd: Double,
  
    val currency: String,
  
    val status: String,
  
    val timestamp: Long,
  
    val payerName: String,
  
    val payerPhone: String,
  
    val channelId: String,
  
    val sourceEmail: String,
  
    val signatureHash: String,
  
    val spaceId: String,
  
    val spaceTitle: String,
  
    val daysGranted: Int,
  
    val userId: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val whishTransaction_upsert: WhishTransactionKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertWhishTransaction"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertWhishTransactionMutation.ref(
  
    id: String,orderId: String,amountUsd: Double,currency: String,status: String,timestamp: Long,payerName: String,payerPhone: String,channelId: String,sourceEmail: String,signatureHash: String,spaceId: String,spaceTitle: String,daysGranted: Int,userId: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertWhishTransactionMutation.Data,
    UpsertWhishTransactionMutation.Variables
  > =
  ref(
    
      UpsertWhishTransactionMutation.Variables(
        id=id,orderId=orderId,amountUsd=amountUsd,currency=currency,status=status,timestamp=timestamp,payerName=payerName,payerPhone=payerPhone,channelId=channelId,sourceEmail=sourceEmail,signatureHash=signatureHash,spaceId=spaceId,spaceTitle=spaceTitle,daysGranted=daysGranted,userId=userId,
  
      )
    
  )

public suspend fun UpsertWhishTransactionMutation.execute(

  
    
      id: String,orderId: String,amountUsd: Double,currency: String,status: String,timestamp: Long,payerName: String,payerPhone: String,channelId: String,sourceEmail: String,signatureHash: String,spaceId: String,spaceTitle: String,daysGranted: Int,userId: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertWhishTransactionMutation.Data,
    UpsertWhishTransactionMutation.Variables
  > =
  ref(
    
      id=id,orderId=orderId,amountUsd=amountUsd,currency=currency,status=status,timestamp=timestamp,payerName=payerName,payerPhone=payerPhone,channelId=channelId,sourceEmail=sourceEmail,signatureHash=signatureHash,spaceId=spaceId,spaceTitle=spaceTitle,daysGranted=daysGranted,userId=userId,
  
    
  ).execute()



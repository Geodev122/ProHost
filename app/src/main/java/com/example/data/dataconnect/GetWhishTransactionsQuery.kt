
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


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface GetWhishTransactionsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetWhishTransactionsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val whishTransactions: List<WhishTransactionsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class WhishTransactionsItem(
  
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
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetWhishTransactions"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetWhishTransactionsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetWhishTransactionsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetWhishTransactionsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetWhishTransactionsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetWhishTransactionsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetWhishTransactionsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }


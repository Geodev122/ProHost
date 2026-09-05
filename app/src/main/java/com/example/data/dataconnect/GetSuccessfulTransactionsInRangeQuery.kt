
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


public interface GetSuccessfulTransactionsInRangeQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetSuccessfulTransactionsInRangeQuery.Data,
      GetSuccessfulTransactionsInRangeQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val startTimestamp: Long,
  
    val endTimestamp: Long,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val whishTransactions: List<WhishTransactionsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class WhishTransactionsItem(
  
    val id: String,
  
    val spaceId: String,
  
    val spaceTitle: String,
  
    val amountUsd: Double,
  
    val currency: String,
  
    val timestamp: Long,
  
    val userId: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetSuccessfulTransactionsInRange"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetSuccessfulTransactionsInRangeQuery.ref(
  
    startTimestamp: Long,endTimestamp: Long,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetSuccessfulTransactionsInRangeQuery.Data,
    GetSuccessfulTransactionsInRangeQuery.Variables
  > =
  ref(
    
      GetSuccessfulTransactionsInRangeQuery.Variables(
        startTimestamp=startTimestamp,endTimestamp=endTimestamp,
  
      )
    
  )

public suspend fun GetSuccessfulTransactionsInRangeQuery.execute(

  
    
      startTimestamp: Long,endTimestamp: Long,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetSuccessfulTransactionsInRangeQuery.Data,
    GetSuccessfulTransactionsInRangeQuery.Variables
  > =
  ref(
    
      startTimestamp=startTimestamp,endTimestamp=endTimestamp,
  
    
  ).execute()


  public fun GetSuccessfulTransactionsInRangeQuery.flow(
    
      startTimestamp: Long,endTimestamp: Long,

  
    
    ): kotlinx.coroutines.flow.Flow<GetSuccessfulTransactionsInRangeQuery.Data> =
    ref(
        
          startTimestamp=startTimestamp,endTimestamp=endTimestamp,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }


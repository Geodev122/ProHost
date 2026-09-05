
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


public interface GetAdminPricingStatesQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetAdminPricingStatesQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val adminPricingStates: List<AdminPricingStatesItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class AdminPricingStatesItem(
  
    val id: String,
  
    val monthlySubscriptionFeeUsd: Double,
  
    val baselineFeeUsd: Double,
  
    val presetOptions: List<Double>,
  
    val merchantChannelId: String,
  
    val merchantSource: String,
  
    val merchantSecretKeyMasked: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetAdminPricingStates"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetAdminPricingStatesQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetAdminPricingStatesQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetAdminPricingStatesQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetAdminPricingStatesQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetAdminPricingStatesQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetAdminPricingStatesQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }


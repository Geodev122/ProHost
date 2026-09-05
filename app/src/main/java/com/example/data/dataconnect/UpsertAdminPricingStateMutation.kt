
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



public interface UpsertAdminPricingStateMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertAdminPricingStateMutation.Data,
      UpsertAdminPricingStateMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val monthlySubscriptionFeeUsd: Double,
  
    val baselineFeeUsd: Double,
  
    val presetOptions: List<Double>,
  
    val merchantChannelId: String,
  
    val merchantSource: String,
  
    val merchantSecretKeyMasked: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val adminPricingState_upsert: AdminPricingStateKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertAdminPricingState"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertAdminPricingStateMutation.ref(
  
    id: String,monthlySubscriptionFeeUsd: Double,baselineFeeUsd: Double,presetOptions: List<Double>,merchantChannelId: String,merchantSource: String,merchantSecretKeyMasked: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertAdminPricingStateMutation.Data,
    UpsertAdminPricingStateMutation.Variables
  > =
  ref(
    
      UpsertAdminPricingStateMutation.Variables(
        id=id,monthlySubscriptionFeeUsd=monthlySubscriptionFeeUsd,baselineFeeUsd=baselineFeeUsd,presetOptions=presetOptions,merchantChannelId=merchantChannelId,merchantSource=merchantSource,merchantSecretKeyMasked=merchantSecretKeyMasked,
  
      )
    
  )

public suspend fun UpsertAdminPricingStateMutation.execute(

  
    
      id: String,monthlySubscriptionFeeUsd: Double,baselineFeeUsd: Double,presetOptions: List<Double>,merchantChannelId: String,merchantSource: String,merchantSecretKeyMasked: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertAdminPricingStateMutation.Data,
    UpsertAdminPricingStateMutation.Variables
  > =
  ref(
    
      id=id,monthlySubscriptionFeeUsd=monthlySubscriptionFeeUsd,baselineFeeUsd=baselineFeeUsd,presetOptions=presetOptions,merchantChannelId=merchantChannelId,merchantSource=merchantSource,merchantSecretKeyMasked=merchantSecretKeyMasked,
  
    
  ).execute()



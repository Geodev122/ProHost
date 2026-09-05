
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


public interface GetSpaceSubdivisionsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetSpaceSubdivisionsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceSubdivisions: List<SpaceSubdivisionsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class SpaceSubdivisionsItem(
  
    val id: String,
  
    val spaceId: String,
  
    val name: String,
  
    val type: String,
  
    val amenities: List<String>,
  
    val strategiesJson: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetSpaceSubdivisions"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetSpaceSubdivisionsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetSpaceSubdivisionsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetSpaceSubdivisionsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetSpaceSubdivisionsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetSpaceSubdivisionsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetSpaceSubdivisionsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }



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


public interface GetSpaceSubdivisionsBySpaceIdQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetSpaceSubdivisionsBySpaceIdQuery.Data,
      GetSpaceSubdivisionsBySpaceIdQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val spaceId: String,
  
  ) {
    
    
  }
  

  
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
    public val operationName: String = "GetSpaceSubdivisionsBySpaceId"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetSpaceSubdivisionsBySpaceIdQuery.ref(
  
    spaceId: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetSpaceSubdivisionsBySpaceIdQuery.Data,
    GetSpaceSubdivisionsBySpaceIdQuery.Variables
  > =
  ref(
    
      GetSpaceSubdivisionsBySpaceIdQuery.Variables(
        spaceId=spaceId,
  
      )
    
  )

public suspend fun GetSpaceSubdivisionsBySpaceIdQuery.execute(

  
    
      spaceId: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetSpaceSubdivisionsBySpaceIdQuery.Data,
    GetSpaceSubdivisionsBySpaceIdQuery.Variables
  > =
  ref(
    
      spaceId=spaceId,
  
    
  ).execute()


  public fun GetSpaceSubdivisionsBySpaceIdQuery.flow(
    
      spaceId: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetSpaceSubdivisionsBySpaceIdQuery.Data> =
    ref(
        
          spaceId=spaceId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }


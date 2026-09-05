
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


public interface GetAuditSecurityLogsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetAuditSecurityLogsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val auditSecurityLogs: List<AuditSecurityLogsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class AuditSecurityLogsItem(
  
    val id: String,
  
    val timestamp: Long,
  
    val actionType: String,
  
    val details: String,
  
    val actorEmail: String,
  
    val severity: String,
  
    val ipAddress: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetAuditSecurityLogs"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetAuditSecurityLogsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetAuditSecurityLogsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetAuditSecurityLogsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetAuditSecurityLogsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetAuditSecurityLogsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetAuditSecurityLogsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }


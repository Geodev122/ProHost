
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



public interface InsertAuditSecurityLogMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      InsertAuditSecurityLogMutation.Data,
      InsertAuditSecurityLogMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val timestamp: Long,
  
    val actionType: String,
  
    val details: String,
  
    val actorEmail: String,
  
    val severity: String,
  
    val ipAddress: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val auditSecurityLog_insert: AuditSecurityLogKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "InsertAuditSecurityLog"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun InsertAuditSecurityLogMutation.ref(
  
    id: String,timestamp: Long,actionType: String,details: String,actorEmail: String,severity: String,ipAddress: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    InsertAuditSecurityLogMutation.Data,
    InsertAuditSecurityLogMutation.Variables
  > =
  ref(
    
      InsertAuditSecurityLogMutation.Variables(
        id=id,timestamp=timestamp,actionType=actionType,details=details,actorEmail=actorEmail,severity=severity,ipAddress=ipAddress,
  
      )
    
  )

public suspend fun InsertAuditSecurityLogMutation.execute(

  
    
      id: String,timestamp: Long,actionType: String,details: String,actorEmail: String,severity: String,ipAddress: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    InsertAuditSecurityLogMutation.Data,
    InsertAuditSecurityLogMutation.Variables
  > =
  ref(
    
      id=id,timestamp=timestamp,actionType=actionType,details=details,actorEmail=actorEmail,severity=severity,ipAddress=ipAddress,
  
    
  ).execute()



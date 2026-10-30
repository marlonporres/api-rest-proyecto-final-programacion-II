package gt.edu.umg.ventas.api.dto;

import java.util.List;

public record OrdenResponse(int id, String numeroOrden, String fecha, ClienteResponse cliente,
                            String estado, String observaciones, List<DetalleOrdenResponse> detalles,
                            String subtotal, String iva, String total, boolean editable, boolean cancelable) {
}
